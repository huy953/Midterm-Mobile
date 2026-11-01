# Android App – Offline-First Todo (Kotlin + Jetpack Compose)

Ứng dụng Android quản lý công việc hoạt động **offline-first**, đồng bộ hai chiều với backend FastAPI trong thư mục `backend/`.

- **Room** là nguồn dữ liệu duy nhất (SSOT) của giao diện: thêm / sửa / xóa / hoàn thành đều chạy được khi mất mạng.
- **WorkManager** tự đồng bộ khi có mạng, tự thử lại với backoff hàm mũ, và dùng unique work để chống chạy trùng.
- Xung đột được giải quyết bằng **Last-Write-Wins** theo `updated_at`, khớp với logic backend.

## 1. Yêu cầu

- Android Studio Koala (2024.1) trở lên, JDK 17 (có sẵn trong Android Studio)
- Emulator hoặc máy thật Android 7.0 (API 24) trở lên
- Python 3.10+ để chạy backend

## 2. Chạy backend

```bash
cd backend
python -m venv venv
# Windows: .\venv\Scripts\Activate.ps1     |  macOS/Linux: source venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

Mở `http://localhost:8000/docs` để thấy Swagger UI.

## 3. Chạy app

1. Android Studio → **File > Open** → chọn thư mục `android/` → chờ Gradle sync.
2. Bấm **Run**.
3. Địa chỉ server mặc định là `http://10.0.2.2:8000/`, dùng được ngay trên Emulator. Muốn đổi thì vào **⋮ > Cấu hình server**:

| Thiết bị | Base URL |
|---|---|
| Emulator | `http://10.0.2.2:8000/` |
| Máy thật cắm cáp (chạy `adb reverse tcp:8000 tcp:8000` trên máy tính) | `http://localhost:8000/` |
| Máy thật cùng Wi-Fi | `http://<IP LAN máy tính>:8000/` (server phải chạy với `--host 0.0.0.0`, và tường lửa cho phép cổng 8000) |

Build APK để nộp: **Build > Build App Bundle(s)/APK(s) > Build APK(s)**, hoặc chạy `./gradlew assembleRelease`. File ra ở `app/build/outputs/apk/`. Bản release được ký bằng debug key nên cài được ngay.

## 4. Kiến trúc

```
UI (Compose)  ──►  TaskViewModel (StateFlow)  ──►  TaskRepository  ──►  Room (TaskDao)   ◄── SSOT
     ▲                                                   │
     └──────────── Flow tự phát lại khi Room đổi ─────────┘
                                                         │ requestSync()
                                                         ▼
                                 SyncScheduler ─► WorkManager ─► SyncWorker ─► SyncEngine ─► Retrofit ─► FastAPI
```

| Thư mục | Nội dung |
|---|---|
| `data/local` | `TaskEntity`, `TaskDao`, `SyncLogEntity`, `AppDatabase` (Room) |
| `data/remote` | `TaskApi` (Retrofit), DTO khớp schema backend, `ApiProvider` |
| `data/repository` | `TaskRepository`: ghi vào Room + đánh dấu `PENDING` + yêu cầu sync |
| `sync` | `SyncEngine` (thuật toán), `SyncWorker` (WorkManager), `SyncScheduler`, `SyncConfig` |
| `ui` | `TaskScreen`, các dialog, `TaskViewModel` |

### Trạng thái đồng bộ của mỗi bản ghi (chỉ lưu trên máy)

| Giá trị | Hiển thị | Ý nghĩa |
|---|---|---|
| `PENDING` | Chưa đồng bộ | Có thay đổi cục bộ chưa được server xác nhận |
| `SYNCING` | Đang đồng bộ | Đang được gửi lên |
| `SYNCED` | Đã đồng bộ | Id nằm trong `applied_ids` của server |
| `FAILED` | Đồng bộ thất bại | Lần gửi gần nhất lỗi (lý do hiện dưới task), sẽ được thử lại |

### Thuật toán đồng bộ (`SyncEngine`)

1. **Push**: lấy mọi bản ghi khác `SYNCED`, đánh dấu `SYNCING`, rồi gọi `POST /tasks/sync` theo lô 100 bản ghi.
   - Id thuộc `applied_ids`: chuyển `SYNCED`. Chỉ chuyển khi `updated_at` chưa đổi, vì người dùng có thể sửa tiếp trong lúc đang gửi; khi đó bản ghi giữ `PENDING` và được gửi ở vòng sau.
   - Id thuộc `ignored_ids`: đây là **xung đột**, server có bản mới hơn. App gọi `GET /tasks/{id}` lấy bản server ghi đè vào Room và ghi nhật ký.
   - Lặp tối đa 3 vòng để gửi nốt các thay đổi phát sinh trong lúc sync.
2. **Pull**: gọi `GET /tasks?since=...`, rồi gộp vào Room như sau:

   | Trạng thái ở máy | Xử lý |
   |---|---|
   | Chưa có bản ghi | Chèn mới |
   | Đã đồng bộ và server mới hơn | Ghi đè |
   | Có thay đổi chưa gửi nhưng server mới hơn | **Xung đột**, server thắng (LWW) |
   | Máy mới hơn | Giữ nguyên |

   Bản ghi `is_deleted = true` vẫn được lưu lại (tombstone), nên task đã xóa không bị tải trở lại.
3. **Lỗi**:
   - Mất mạng, timeout, server tắt, HTTP 5xx/408/429: WorkManager tự `retry` với backoff hàm mũ 10s → 20s → 40s… tối đa 6 lần.
   - HTTP 4xx khác: dừng, đánh dấu `FAILED`.
4. **Chống trùng (idempotency)**:
   - `id` là UUID v4 do client sinh, và server so `updated_at`. Vì vậy gửi lại cùng payload không tạo bản ghi mới.
   - Unique work dùng `KEEP`, nên 50 thao tác offline chỉ sinh 1 tác vụ sync. Có thêm `Mutex` để không bao giờ có 2 lần sync chạy song song.
5. Có **sync định kỳ 15 phút** (mức tối thiểu của Android) để kéo thay đổi từ thiết bị khác về. Ngoài ra app sync mỗi khi mở và khi bấm **Đồng bộ ngay**.

### Khi nào chính sách LWW làm mất dữ liệu

- Hai máy cùng sửa một task khi offline: chỉ bản có `updated_at` lớn hơn được giữ, bản kia bị loại. Bản bị loại vẫn ghi lại trong **Nhật ký đồng bộ**.
- `updated_at` lấy theo **đồng hồ thiết bị**. Nếu một máy chạy sai giờ (nhanh hơn), bản sửa của máy đó luôn thắng, dù thực tế được sửa trước.
- Sửa và xóa cùng lúc: nếu lệnh xóa có `updated_at` mới hơn thì nội dung sửa bị mất, và ngược lại thì task "sống lại".

## 5. Kịch bản kiểm thử (TC01 – TC09)

Bật/tắt mạng trên Emulator: kéo thanh thông báo → **Airplane mode**. Hoặc chạy `adb shell svc wifi disable` và `adb shell svc data disable`.

| Mã | Cách làm | Quan sát |
|---|---|---|
| TC01 | Có mạng, thêm task | Badge chuyển Chưa đồng bộ → Đang đồng bộ → **Đã đồng bộ**. Gọi `GET /tasks` trên Swagger thấy task |
| TC02 | Bật Airplane mode, thêm và sửa task | Thẻ trạng thái đỏ **OFFLINE**, task hiện ngay với badge **Chưa đồng bộ**, không crash |
| TC03 | Tắt Airplane mode | Không cần bấm gì, sau vài giây tất cả task chuyển **Đã đồng bộ** |
| TC04 | Offline, thêm task → vuốt tắt app trong Recents → mở lại → bật mạng | Task vẫn còn. WorkManager vẫn tự đồng bộ, kể cả khi app đang đóng |
| TC05 | Tắt server (Ctrl+C uvicorn), có mạng, thêm task | Badge **Đồng bộ thất bại** kèm lý do. Thẻ trạng thái báo "sẽ tự thử lại (lần n)". Bật lại server thì tự đồng bộ, hoặc bấm *Đồng bộ ngay* |
| TC06 | Bấm **Đồng bộ ngay** liên tục nhiều lần | `GET /tasks` không có bản ghi trùng. Nhật ký cho thấy các lần sau chỉ "kéo về 0 thay đổi" |
| TC07 | Xem mục 5.1 | Nhật ký có dòng **[XUNG ĐỘT]**, ghi rõ bản nào thắng và bản nào bị loại |
| TC08 | Offline, xóa task → bật mạng | Task biến mất, `GET /tasks?since=0` thấy `is_deleted: true`, các lần sync sau không tải lại task |
| TC09 | Offline, tạo 50–100 task → bật mạng | Mở **⋮ > Nhật ký đồng bộ** để xem thời gian (ms), số bản ghi đẩy/nhận, tỷ lệ thành công. Đối chiếu số lượng với `GET /tasks` để kiểm tra trùng/thiếu |

### 5.1. Mô phỏng 2 thiết bị (TC07)

**Cách A: hai Emulator.** Chạy 2 AVD cùng trỏ `http://10.0.2.2:8000/`.
1. Đồng bộ để cả hai cùng có task X.
2. Bật Airplane mode cả hai máy.
3. Máy 1 sửa X thành "A", sau đó máy 2 sửa X thành "B".
4. Bật mạng máy 1, rồi bật mạng máy 2.
5. Kết quả: "B" thắng vì sửa muộn hơn. Bấm *Đồng bộ ngay* trên máy 1 để thấy máy 1 cũng đổi thành "B" và có log xung đột.

**Cách B: một Emulator + Swagger đóng vai thiết bị thứ hai.**
1. Task X đã đồng bộ.
2. Bật Airplane mode trên máy, sửa X thành "Máy".
3. Trên Swagger gọi `PUT /tasks/{id}` với `{"title": "Server"}`. Server tự gán `updated_at` = bây giờ, tức muộn hơn lần sửa trên máy.
4. Tắt Airplane mode trên máy. Server trả id trong `ignored_ids`, app hiển thị "Server" và ghi nhật ký xung đột.
5. Làm ngược thứ tự (PUT trước, sửa trên máy sau) thì bản trên máy thắng.

## 6. Ghi chú cho backend

- Code backend **không có** tiền tố `/api/v1`, trong khi docs D4/D5 và README gốc có. App đang gọi theo code: `/tasks`, `/tasks/sync`. Nếu backend thêm tiền tố, chỉ cần đổi Base URL thành `http://host:8000/api/v1/`.
- `GET /tasks?since=` lọc theo `updated_at`, mà giá trị này là **giờ client lúc sửa**. Một thay đổi offline đẩy lên muộn sẽ không được máy khác kéo về theo kiểu tăng dần. Vì vậy app đang để `SyncConfig.FULL_PULL = true` (luôn gọi `since=0`). Khi backend thêm cột "thời điểm server nhận" và lọc theo cột đó thì đổi thành `false`.
- Backend chưa có trường `description`, nên app chỉ có tiêu đề.

## 7. Migration Room

Schema hiện là version 1 và đang dùng `fallbackToDestructiveMigration()`. Khi thêm cột (ví dụ `description`), tăng `version` lên 2 và khai báo migration:

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN description TEXT")
    }
}
// Room.databaseBuilder(...).addMigrations(MIGRATION_1_2)
```
