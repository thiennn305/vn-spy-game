# Project Rules: VN Spy Party Game ("Tòa Án Mèo Đêm / Undercover Cats")

## 1. Tech Stack & Hybrid Storage Architecture
- **Backend:** Java 21, Spring Boot 3.3+ (Spring Web, Thymeleaf, Spring Data JPA, Jackson).
- **Hybrid Storage Strategy:**
  - **In-Memory (`ConcurrentHashMap`):** Dùng cho toàn bộ trạng thái phòng đang chơi (`GameRoom`, `Player`, trạng thái Vote, đồng hồ đếm ngược, kết nối SSE). Tự động dọn dẹp phòng không hoạt động sau 2 giờ.
  - **Database (`Spring Data JPA` + `H2 File-based DB` tại `./data/spydb`):** Dùng để lưu trữ lâu dài các bộ từ khóa cá nhân do người dùng tự tạo (`CustomDeck` và `CustomWordPair`).
- **Frontend & Real-time Sync:**
  - Server-Side Rendering với Thymeleaf, HTMX (1.9.12 + extension `sse`), Bootstrap 5.3.3 và Bootstrap Icons.
  - Sử dụng `SseEmitter` của Spring Boot kết hợp `hx-ext="sse"` của HTMX để đồng bộ giao diện thời gian thực cho toàn bộ người chơi trong phòng mà không cần tải lại trang.
- **Multi-Language & Cultural Localization (`vi` / `en`):**
  - Sử dụng `MessageSource` (`messages_vi.properties`, `messages_en.properties`) và `CookieLocaleResolver` (mặc định là `vi`).
  - Khi chuyển đổi ngôn ngữ (`vi` <-> `en`), toàn bộ giao diện và kho chủ đề mặc định phải tự động chuyển sang kho dữ liệu văn hóa tương ứng (`data/game-data-vi.json` và `data/game-data-en.json`).

## 2. Game Mechanics: 3 Roles, Word-Count Parity & Last Guess
- **3 Roles System:**
  - `CIVILIAN` (Dân thường - Mèo Ngơ Ngác): Nhận `civilianWord`.
  - `SPY` (Gián điệp - Mèo Mờ Ám): Nhận `spyWord`.
  - `MR_WHITE` (Trùm Mù - Mèo Đội Thùng Carton): Mở khóa khi phòng >= 4 người và Host bật. Nhận thẻ trắng (`secretWord = null`). Quy tắc chia lượt nói Vòng 1: `MR_WHITE` không bao giờ bị xếp ở vị trí phát biểu đầu tiên.
- **Word-Count Parity Rule (Đồng bộ số lượng chữ 100%):**
  - Trong mọi cặp từ (`WordPair`), `civilianWord` và `spyWord` BẮT BUỘC phải có cùng số lượng tiếng/từ (tính bằng `trim().split("\\s+").length`).
- **Last Guess (Cơ hội Lật kèo 20 giây khi bị Vote loại):**
  - Khi người bị Vote loại là `SPY` hoặc `MR_WHITE`, chuyển trạng thái phòng sang `LAST_GUESS` trong 20 giây trước khi xét thắng thua.
  - Trên màn hình người bị loại: Hiển thị Tên chủ đề, Tổng số chữ, khung gợi ý độ dài từng chữ dạng gạch dưới kèm chữ cái đầu tiên (VD: `B _ _   đ _ _   m _ _   t _ _`), và ô nhập đáp án.
  - **Smart Matching:** So sánh đáp án sau khi đã `trim()`, `toLowerCase()`, và bỏ dấu tiếng Việt (`Normalizer.Form.NFD`). Nếu khớp -> Phe `SPY` hoặc `MR_WHITE` lập tức giành chiến thắng lật kèo.
- **Win Conditions & Penalty Spin:**
  - `CIVILIAN` thắng khi loại hết toàn bộ `SPY` và `MR_WHITE`.
  - Phe phản diện (`SPY` / `MR_WHITE`) thắng khi sống sót đến lúc phòng chỉ còn 2 người hoặc đoán đúng từ khóa ở pha `LAST_GUESS`.
  - Màn hình `RESULT` có vòng quay chọn ngẫu nhiên 1 hình phạt (`Penalty`) vui nhộn cho phe thua cuộc.

## 3. Persistent Custom Deck & AI Topic Generator
- **Zero-Friction User Ownership:**
  - Tự động sinh và lưu `creatorToken` (UUID) vào HTTP Cookie (max-age 365 ngày) và `localStorage`.
  - Hỗ trợ gắn thêm `recoveryPin` (4–15 ký tự chữ/số, không khoảng trắng) vào `CustomDeck` để người dùng khôi phục danh sách chủ đề cá nhân khi đổi thiết bị.
  - Chỉ người tạo có `creatorToken` khớp (hoặc nhập đúng `recoveryPin`) mới nhìn thấy, sửa, xóa hoặc sử dụng các `CustomDeck` của riêng mình khi làm Host.
- **3-Mode Custom Deck Modal (Host Only):**
  - Cho phép quản lý danh sách chủ đề đã lưu và tạo mới qua 3 Tab: (1) **Nhập tay trực tiếp (Dynamic Form Rows - Mặc định)**, (2) **Dán nhanh (Quick Paste)**, (3) **Upload File (.json/.csv <= 100KB)**.
- **AI Topic Generator (Gemini API):**
  - Đọc khóa API từ biến môi trường `GEMINI_API_KEY` (`${GEMINI_API_KEY:}` trong `application.properties`). Tuyệt đối không hardcode API Key trong source code.
  - Sử dụng `RestClient` gọi endpoint `gemini-2.5-flash:generateContent` với `responseMimeType: "application/json"` và `responseSchema` (`targetWordCount`, `civilianWord`, `spyWord`).
  - Yêu cầu Gemini sinh 15 cặp từ mỗi lần gọi, chuẩn hóa khoảng trắng/gạch nối và lọc cứng bằng Java để lấy ra đúng 10 cặp có `countWords(civilianWord) == countWords(spyWord)` điền thẳng vào các dòng của Tab Nhập tay.
  - Áp dụng Cooldown 15 giây cho mỗi phòng/token để chống spam API.

## 4. Client-Side & Server-Side Real-Time Validation Rules
- Sử dụng Vanilla JS bắt sự kiện `input` để kiểm tra và hiển thị trạng thái hợp lệ/lỗi trực quan ngay lập tức:
  1. `nickname`: 2–15 ký tự, không khoảng trắng ở hai đầu, không có 2 khoảng trắng liên tiếp, chỉ cho phép chữ cái (có dấu tiếng Việt), chữ số và gạch dưới.
  2. `roomCode`: Đúng 5 ký tự chữ in hoa hoặc số (`A-Z`, `0-9`), tự động chuyển chữ thường thành chữ hoa khi gõ, tuyệt đối không chứa khoảng trắng.
  3. `deckName` (Tên chủ đề): 3–30 ký tự, không khoảng trắng thừa hai đầu, không chứa thẻ HTML/script.
  4. `wordPairs` (Trong Custom Deck): Mỗi bộ từ 3–50 cặp. Mỗi từ dài 2–40 ký tự, hai từ trong cùng 1 cặp không được trùng nhau và **bắt buộc có cùng số lượng chữ** (hiển thị badge đếm số từ trực tiếp như `[4 từ] - [4 từ]` trên từng dòng). Chỉ mở khóa nút Submit/Lưu khi mọi điều kiện đều hợp lệ.

## 5. Visual Theme: "Cute & Mysterious Detective Cats" (`static/css/cat-theme.css`)
- **Mobile-first Layout:** Tối ưu cho màn hình dọc điện thoại (khung chơi chính giới hạn `max-width: 480px` căn giữa trên màn hình lớn).
- **Typography:** Nhúng Google Fonts `Fredoka` (tiêu đề, nút bấm, từ khóa) và `Quicksand` (văn bản, form input) hỗ trợ đầy đủ tiếng Việt.
- **Color Palette & Neo-Brutalism Cartoon Styling:**
  - Background chính: `#1A162B` (Dark Mystery Purple) với họa tiết dấu chân mèo (`🐾`) chìm mờ.
  - Card Surface: `#FFF8E7` (Warm Cream) chữ tối `#1A162B`, hoặc `#2D264B` chữ sáng.
  - Accent Colors: Cat-Eye Gold (`#FFB800`), Paw Pink (`#FF6B8B`), Catnip Mint (`#00E5A3`).
  - Mọi card, modal, button và input có viền hoạt hình `border: 3px solid #120E1E`, `border-radius: 1.25rem`, và bóng đổ cứng `box-shadow: 0 5px 0 #120E1E`. Hiệu ứng nhấn nút vật lý khi `:active` (`transform: translateY(3px); box-shadow: 0 2px 0 #120E1E`).
- **Custom Inline SVG Cat Illustrations & Micro-interactions:**
  - Sử dụng Inline SVG hoạt hình mèo có CSS animation nhẹ cho: Logo Mèo Thám Tử, 8 mẫu Avatar mèo biểu cảm trong phòng chờ, và 3 thẻ bài vai trò (Mèo Ngơ Ngác, Mèo Kính Râm Mờ Ám, Mèo Đội Thùng Carton).
  - Thẻ bài từ khóa thiết kế lật 3D (`perspective: 1000px`): Mặt lưng có đệm chân mèo hồng phát sáng nhịp thở, người chơi phải **nhấn và giữ** để lật xem từ khóa, thả tay ra tự động úp lại.
  - Văn phong thông báo trạng thái và báo lỗi validation hài hước theo chủ đề "Hoàng thượng - Con sen / Tòa Án Mèo Đêm".

## 6. Audio System: Mystery Cat BGM & Interactive SFX (`static/js/cat-audio.js`)
- **Zero-Dependency Web Audio API:** Sử dụng thuần `window.AudioContext` để tổng hợp cả Nhạc nền (Procedural Mystery/Noir BGM) và Hiệu ứng âm thanh (SFX), không phụ thuộc file âm thanh bên ngoài.
- **Background Music (BGM) Specs:**
  - Giai điệu chậm rãi, bí ẩn (Walking Bass + Pizzicato Melody nhịp ~75 BPM, lọc âm trầm qua `BiquadFilterNode`), âm lượng mặc định vừa đủ nghe (`gain = 0.18`), có nút Mute và thanh trượt âm lượng lưu vào `localStorage`.
  - **Smart Room Audio:** Mặc định BGM chỉ tự động kích hoạt trên máy của `Host` (Chủ phòng) sau thao tác chạm đầu tiên và tắt sẵn trên máy thành viên thường để tránh dội âm khi ngồi chung bàn (thành viên vẫn có thể bật thủ công trên Header).
  - Tự động tăng tốc độ nhịp nhạc nền khi phòng chuyển sang trạng thái `VOTING` hoặc `LAST_GUESS`.
- **Sound Effects (SFX) Triggers:**
  - `playMeow()`: Khi vào phòng, lưu Custom Deck thành công hoặc nhập dữ liệu hợp lệ.
  - `playHiss()`: Khi JS validation phát hiện lỗi nhập liệu hoặc khi bấm nút Vote kết tội.
  - `playPurrFlip()`: Khi chạm giữ vào đệm chân mèo để lật xem thẻ bài bí mật.
  - `playTick()` & `playPenaltySpin()`: Khi đếm ngược 20s lật kèo và khi quay vòng quay Hình phạt cuối ván.