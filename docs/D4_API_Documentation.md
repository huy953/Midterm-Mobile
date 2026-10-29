# 📘 D4: TÀI LIỆU ĐẶC TẢ RESTFUL API & HƯỚNG DẪN BACKEND
**Đề tài:** Ứng dụng Android Offline-First với Two-Way Sync Engine  
**Người phụ trách:** Thành viên B (Backend & Sync Engine Lead)  
**Công nghệ:** Python FastAPI, SQLAlchemy, SQLite/PostgreSQL, Pydantic v2

---

## 1. TỔNG QUAN HỆ THỐNG API

FastAPI đóng vai trò là máy chủ trung tâm lưu trữ dữ liệu và giải quyết xung đột khi các máy Android gửi dữ liệu đồng bộ lên.
- **Base URL:** `http://<server-ip>:8000/api/v1`
- **Swagger UI Tài liệu tương tác:** `http://localhost:8000/docs`
- **ReDoc:** `http://localhost:8000/redoc`

---

## 2. QUY ƯỚC DỮ LIỆU (DATA CONTRACT)

Bảng `tasks`:

| Tên trường | Kiểu dữ liệu | Bắt buộc | Mô tả |
| :--- | :--- | :---: | :--- |
| `id` | `VARCHAR(36)` | Có | Khóa chính dạng chuỗi UUID v4 do Android Client sinh. |
| `title` | `VARCHAR(255)` | Có | Tiêu đề công việc. |
| `description` | `TEXT` | Không | Mô tả chi tiết (cho phép `null`). |
| `is_completed` | `BOOLEAN` | Có | `true`: Đã xong, `false`: Chưa xong. |
| `updated_at` | `BIGINT` | Có | Unix timestamp (milliseconds) phục vụ Last-Write-Wins (LWW). |
| `is_deleted` | `BOOLEAN` | Có | Cờ xóa mềm (Soft Delete). |

---

## 3. DANH SÁCH RESTFUL ENDPOINTS

### 3.1. Nhóm Healthcheck & Test Reset
- **`GET /api/v1/health`**
  - Trả về: `{"status": "healthy", "server_time": 1729605600000}`
- **`POST /api/v1/tasks/reset`**
  - Xóa sạch toàn bộ dữ liệu trong bảng `tasks` để chạy lại các kịch bản kiểm thử (TC01-TC09).

### 3.2. Nhóm CRUD Đơn lẻ
- **`GET /api/v1/tasks`**
  - *Query Param:* `since_timestamp` (tùy chọn).
  - Không truyền `since_timestamp`: Trả về các task `is_deleted == false`.
  - Có truyền `since_timestamp`: Trả về các task có `updated_at > since_timestamp` (gồm cả `is_deleted == true`).
- **`POST /api/v1/tasks`**
  - Tạo mới 1 task đơn lẻ.
- **`GET /api/v1/tasks/{task_id}`**
  - Lấy chi tiết 1 task theo UUID.
- **`PUT /api/v1/tasks/{task_id}`**
  - Cập nhật thông tin task, server tự động cập nhật `updated_at` theo giờ hiện tại.
- **`DELETE /api/v1/tasks/{task_id}`**
  - Xóa mềm: Chuyển `is_deleted = true`, cập nhật `updated_at`.

### 3.3. Nhóm Đồng bộ Lô (Batch Sync)
- **`POST /api/v1/tasks/sync`**
  - Nhận `client_last_sync_timestamp` và mảng `tasks` từ Client.
  - Phân giải xung đột Last-Write-Wins (LWW).
  - Trả về `server_timestamp`, `synced_count` và mảng `server_updates`.
