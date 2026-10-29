# 📘 D5: THUẬT TOÁN ĐỒNG BỘ HAI CHIỀU & PHÂN GIẢI XUNG ĐỘT (LWW)
**Đề tài:** Ứng dụng Android Offline-First với Two-Way Sync Engine  
**Người phụ trách:** Thành viên B (Backend & Sync Engine Lead)  

---

## 1. NGUYÊN LÝ THUẬT TOÁN LAST-WRITE-WINS (LWW)

Trong mô hình **Offline-First**, thiết bị Client có thể chỉnh sửa dữ liệu khi không có mạng trong nhiều giờ hoặc nhiều ngày. Khi kết nối Internet được thiết lập lại:
1. Nhiều thiết bị có thể cùng chỉnh sửa một bản ghi Task $T$.
2. Để thống nhất trạng thái cuối cùng mà không cần tương tác thủ công từ người dùng, hệ thống sử dụng trường `updated_at` (Unix epoch tính bằng milliseconds).
3. **Quy tắc phán quyết:** Bản ghi nào có mốc thời gian `updated_at` lớn hơn (được chỉnh sửa sau cùng) sẽ **chiến thắng** và trở thành dữ liệu chuẩn.

---

## 2. LƯU ĐỒ THUẬT TOÁN ĐỒNG BỘ PHÍA SERVER (PUSH & PULL)

Khi Client gọi `POST /api/v1/tasks/sync`:

```mermaid
flowchart TD
    Start([Bắt đầu Sync Request]) --> ReadClient[Đọc mảng tasks và client_last_sync_timestamp]
    ReadClient --> LoopTasks{Lặp qua từng task}
    
    LoopTasks -->|Còn task| CheckExists{Task đã có trong Server DB?}
    CheckExists -->|Chưa có| InsertNew[Thêm mới vào DB\nGhi nhận Client Thắng]
    CheckExists -->|Đã có| CompareTime{client.updated_at >= server.updated_at?}
    
    CompareTime -->|Đúng| UpdateServer[Ghi đè bản ghi Server bằng bản ghi Client\nGhi nhận Client Thắng]
    CompareTime -->|Sai| KeepServer[Bỏ qua thay đổi Client\nServer thắng xung đột]
    
    InsertNew --> LoopTasks
    UpdateServer --> LoopTasks
    KeepServer --> LoopTasks
    
    LoopTasks -->|Hết task| CommitDB[Commit Transaction vào Database]
    CommitDB --> PullUpdates[Truy vấn Server Task có updated_at > client_last_sync_timestamp]
    PullUpdates --> FilterUpdates[Loại trừ các Task Client vừa gửi mà Client đã thắng]
    FilterUpdates --> BuildResponse[Đóng gói server_updates + server_timestamp]
    BuildResponse --> End([Trả về HTTP 200 OK])
```

---

## 3. CÁC TÌNH HUỐNG KIỂM THỬ THỰC TẾ

1. **Client tạo mới khi Offline:** Server nhận ID UUID v4, thêm mới vào cơ sở dữ liệu.
2. **Client cập nhật task khi Offline:** `client.updated_at` > `server.updated_at` -> Server cập nhật dữ liệu mới.
3. **Hai Client cùng sửa 1 task khi Offline (Xung đột):**
   - Thiết bị A sửa lúc $t_1$, thiết bị B sửa lúc $t_2$ ($t_2 > t_1$).
   - Thiết bị A đồng bộ trước -> Server nhận bản ghi ở $t_1$.
   - Thiết bị B đồng bộ sau -> Do $t_2 > t_1$, Server cập nhật theo bản ghi của Thiết bị B.
   - Khi Thiết bị A đồng bộ lần kế tiếp -> Server gửi bản ghi $t_2$ về cho Thiết bị A cập nhật lại Room DB.
4. **Xóa mềm (Soft Delete):** Task bị xóa có `is_deleted = true`. Thuật toán LWW xử lý cờ này như một bản cập nhật thông thường, lan truyền trạng thái xóa sang các Client khác mà không gây mất dấu lịch sử.
