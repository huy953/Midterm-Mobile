# Backend Đồng Bộ Hai Chiều Cho Ứng Dụng Android Offline-First

Hệ thống Backend được thiết kế và xây dựng theo chuẩn **Layered Architecture** (Kiến trúc phân tầng) sử dụng **FastAPI**, **SQLAlchemy** và **SQLite**, phục vụ cơ chế đồng bộ hai chiều (Two-Way Synchronization) cho ứng dụng di động Android hoạt động theo mô hình **Offline-First**.

---

## 1. Kiến Trúc Hệ Thống (Layered Architecture)

Hệ thống được tổ chức thành các tầng phân tách độc lập (Separation of Concerns), giúp mã nguồn dễ bảo trì, mở rộng và kiểm thử:

```
backend/
├── app/
│   ├── __init__.py
│   ├── main.py               # Điểm khởi tạo FastAPI app, cấu hình CORS, routing
│   ├── config.py             # Cấu hình Pydantic BaseSettings, biến môi trường
│   ├── database.py           # Kết nối SQLAlchemy engine, SessionLocal, get_db
│   ├── models/               # Tầng Database Models (ORM)
│   │   ├── __init__.py
│   │   └── task.py           # SQLAlchemy Task model
│   ├── schemas/              # Tầng Data Transfer Objects (DTO) & Pydantic Schemas
│   │   ├── __init__.py
│   │   └── task.py           # TaskBase, TaskCreate, TaskDTO, SyncPayload, SyncResult
│   ├── crud/                 # Tầng Data Access Layer (CRUD)
│   │   ├── __init__.py
│   │   └── task.py           # Các thao tác truy vấn cơ sở dữ liệu
│   ├── services/             # Tầng Business Logic & Synchronization Engine
│   │   ├── __init__.py
│   │   └── sync_service.py   # Thuật toán Last-Write-Wins (LWW), xử lý batch sync
│   └── routers/              # Tầng Presentation / REST API Endpoints
│       ├── __init__.py
│       └── tasks.py          # Định tuyến HTTP endpoints
├── tests/
│   ├── __init__.py
│   ├── conftest.py           # Fixture SQLite in-memory & FastAPI TestClient
│   ├── test_api.py           # Unit & Integration tests cho REST API
│   └── test_sync.py          # Kiểm thử Idempotency (TC06), LWW (TC07), Soft Delete (TC08)
├── requirements.txt          # Danh sách thư viện phụ thuộc
└── README.md                 # Tài liệu hướng dẫn triển khai và tích hợp
```

---

## 2. Mô Hình Dữ Liệu & Nguyên Lý Đồng Bộ

### 2.1. Thực thể Task
| Thuộc tính | Kiểu dữ liệu | Ý nghĩa |
| :--- | :--- | :--- |
| `id` | `VARCHAR(36)` | Khóa chính, UUIDv4 do Android client tự sinh khi tạo mới offline |
| `title` | `VARCHAR(255)` | Tiêu đề công việc |
| `is_completed` | `BOOLEAN` | Trạng thái hoàn thành (`False` / `True`) |
| `is_deleted` | `BOOLEAN` | Cờ xóa logic (Soft Delete) |
| `updated_at` | `BIGINT` | Unix Epoch timestamp (mili-giây) dùng để so khớp xung đột |

### 2.2. Chiến lược giải quyết xung đột: Last-Write-Wins (LWW)
Khi thiết bị Android đẩy danh sách công việc lên thông qua endpoint `POST /tasks/sync`:
1. **Bản ghi chưa tồn tại trên Server**: Server thực hiện `INSERT` bản ghi mới và ghi nhận ID vào danh sách `applied_ids`.
2. **Bản ghi đã tồn tại**: Server so khớp giá trị `updated_at`:
   - Nếu `client.updated_at > server.updated_at`: Bản ghi từ client mới hơn $\rightarrow$ Server ghi đè dữ liệu và ghi nhận ID vào `applied_ids`.
   - Nếu `client.updated_at == server.updated_at`: Yêu cầu gửi lặp (Idempotent retry) $\rightarrow$ Giữ nguyên dữ liệu, phản hồi an toàn vào `applied_ids`.
   - Nếu `client.updated_at < server.updated_at`: Bản ghi trên server mới hơn bản ghi client gửi lên $\rightarrow$ Bỏ qua ghi đè để bảo toàn dữ liệu mới nhất, ghi nhận ID vào `ignored_ids`.

### 2.3. Xóa logic (Soft Delete)
Để hỗ trợ ứng dụng hoạt động ngoại tuyến:
- Khi người dùng xóa task, hệ thống **không** xóa vật lý (`DELETE FROM tasks`).
- Bản ghi được cập nhật `is_deleted = True` và nâng `updated_at` lên thời điểm hiện tại.
- Khi các thiết bị khác thực hiện **Incremental Pull** (`GET /tasks?since={last_sync_time}`), server vẫn trả về các bản ghi này để Room SQLite phía Android biết và cập nhật/xóa khỏi giao diện người dùng.

### 2.4. Tính Lặp An Toàn (Idempotency)
Nếu kết nối mạng chập chờn khiến client gửi lại cùng một payload nhiều lần, cơ chế LWW đảm bảo không gây ra lỗi và không sinh ra các bản ghi trùng lặp (không duplicate task ID).

---

## 3. Cài Đặt & Khởi Chạy Backend

### 3.1. Yêu cầu môi trường
- Python 3.10 trở lên.
- Trình quản lý gói `pip`.

### 3.2. Cài đặt thư viện
Tại thư mục `backend`:
```bash
# Tạo môi trường ảo (nếu chưa có)
python -m venv venv

# Kích hoạt môi trường ảo:
# Windows (PowerShell):
.\venv\Scripts\Activate.ps1
# Windows (CMD):
.\venv\Scripts\activate.bat
# Linux / macOS:
source venv/bin/activate

# Cài đặt các gói phụ thuộc
pip install -r requirements.txt
```

### 3.3. Khởi chạy Server
Chạy máy chủ Uvicorn lắng nghe trên mọi giao tiếp mạng (`0.0.0.0`) để máy ảo Android và thiết bị thật đều có thể kết nối:
```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

- Truy cập Swagger UI trực quan: `http://localhost:8000/docs`
- Kiểm tra trạng thái máy chủ: `http://localhost:8000/health`

---

## 4. Hướng Dẫn Kết Nối Từ Thiết Bị Android

### 4.1. Cấu hình địa chỉ Base URL trong Android Retrofit
1. **Máy ảo Android Studio Emulator**:
   ```kotlin
   const val BASE_URL = "http://10.0.2.2:8000/"
   ```
   *(Địa chỉ `10.0.2.2` là alias đặc biệt trỏ về `127.0.0.1` của máy tính host).*

2. **Thiết bị thật kết nối qua cùng mạng Wi-Fi**:
   Xác định địa chỉ IP nội bộ của máy chủ (dùng lệnh `ipconfig` trên Windows hoặc `ifconfig`/`ip a` trên Linux):
   ```kotlin
   const val BASE_URL = "http://192.168.1.X:8000/"
   ```

### 4.2. Cho phép HTTP Cleartext trong Android
Trong tệp `AndroidManifest.xml`, thêm thuộc tính:
```xml
<application
    android:usesCleartextTraffic="true"
    ...>
```
Hoặc cấu hình bảo mật `res/xml/network_security_config.xml` cho phép domain `10.0.2.2` và IP nội bộ.

---

## 5. Danh Sách API Endpoints

### 5.1. Incremental Pull (Lấy dữ liệu đồng bộ tăng dần)
- **Method**: `GET`
- **Path**: `/tasks?since={timestamp}`
- **Tham số Query**:
  - `since` (mặc định = 0): Timestamp Unix epoch tính bằng mili-giây.
- **Phản hồi**: `200 OK`
```json
[
  {
    "id": "e4b6c968-3e4a-4a25-83e9-79f90240db71",
    "title": "Hoàn thành bài tập Mobile",
    "is_completed": true,
    "is_deleted": false,
    "updated_at": 1700000003000
  }
]
```

### 5.2. Batch Push Sync (Đồng bộ đẩy lên kèm Last-Write-Wins)
- **Method**: `POST`
- **Path**: `/tasks/sync`
- **Request Body** (chấp nhận object `{"tasks": [...]}` hoặc mảng `[...]`):
```json
{
  "tasks": [
    {
      "id": "e4b6c968-3e4a-4a25-83e9-79f90240db71",
      "title": "Hoàn thành bài tập Mobile (Client update)",
      "is_completed": true,
      "is_deleted": false,
      "updated_at": 1700000005000
    }
  ]
}
```
- **Phản hồi**: `200 OK`
```json
{
  "applied_ids": [
    "e4b6c968-3e4a-4a25-83e9-79f90240db71"
  ],
  "ignored_ids": [],
  "server_time": 1700000005120
}
```

### 5.3. Tạo Mới Task Trực Tiếp (Client Test)
- **Method**: `POST`
- **Path**: `/tasks`
- **Request Body**:
```json
{
  "title": "Chuẩn bị thuyết trình",
  "is_completed": false
}
```
- **Phản hồi**: `201 Created`

### 5.4. Cập Nhật Task
- **Method**: `PUT`
- **Path**: `/tasks/{task_id}`
- **Request Body**:
```json
{
  "title": "Chuẩn bị thuyết trình (Đã chỉnh sửa)",
  "is_completed": true
}
```
- **Phản hồi**: `200 OK`

### 5.5. Xóa Logic Task (Soft Delete)
- **Method**: `DELETE`
- **Path**: `/tasks/{task_id}`
- **Phản hồi**: `200 OK`
```json
{
  "id": "e4b6c968-3e4a-4a25-83e9-79f90240db71",
  "title": "Chuẩn bị thuyết trình",
  "is_completed": true,
  "is_deleted": true,
  "updated_at": 1700000010000
}
```

---

## 6. Chạy Bộ Kiểm Thử Tự Động (Test Suite)

Hệ thống đi kèm bộ kiểm thử toàn diện với SQLite in-memory, kiểm chứng độc lập và không ảnh hưởng đến cơ sở dữ liệu thật:
```bash
# Đứng tại thư mục backend và chạy pytest
pytest -v
```

### Các kịch bản kiểm thử trọng tâm:
1. `test_api.py`: Kiểm thử toàn diện các luồng CRUD, HTTP 404, lọc tăng dần qua query param `since`.
2. `test_sync.py::test_tc06_idempotency_repeated_sync`: Kiểm thử gửi lặp payload (Idempotency).
3. `test_sync.py::test_tc07_conflict_resolution_last_write_wins`: Kiểm thử xung đột LWW (bỏ qua bản ghi cũ, áp dụng bản ghi mới).
4. `test_sync.py::test_tc08_soft_delete_synchronization`: Kiểm thử đồng bộ xóa logic hai chiều.
