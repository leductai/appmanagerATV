# TV App Manager — Đặc tả yêu cầu sản phẩm

> Phiên bản tài liệu: 1.0  
> Nền tảng: Android TV / TV Box, Android 9 (API 28) trở lên  
> Công nghệ dự kiến: Kotlin, Jetpack Compose for TV, DataStore, OkHttp/Retrofit, PackageInstaller

## 1. Mục tiêu

TV App Manager là ứng dụng quản lý và triển khai APK cá nhân trên Android TV. Ứng dụng đọc một **manifest JSON** do người dùng quản lý, so sánh version trong manifest với các app đang cài trên TV, sau đó tải APK từ link trực tiếp và yêu cầu Android cài đặt hoặc cập nhật.

Ứng dụng được thiết kế để dùng hoàn toàn bằng remote, đặc biệt ưu tiên thao tác đơn giản cho người lớn tuổi: mở app, nhấn mũi tên lên để đến hành động chính, nhấn OK và xác nhận cài đặt.

## 2. Phạm vi sản phẩm

### Có trong MVP

- Đọc manifest JSON từ link HTTPS hoặc file local.
- So sánh version trong manifest với version của package trên TV.
- Cài APK, cập nhật APK, gỡ app người dùng.
- Hai hàng đợi: **Cập nhật tất cả** và **Cài tất cả**.
- Chọn/bỏ chọn app trước khi chạy hàng đợi.
- Điều hướng hoàn toàn bằng D-pad remote.
- Song ngữ Tiếng Việt và English.
- Cài đặt là menu ẩn, mở bằng chuỗi phím remote `8-8-0-0`.

### Không thuộc MVP

- Cài đặt im lặng; Android TV không root vẫn cần người dùng xác nhận hộp thoại cài đặt hệ thống.
- GitHub API, tự dò release, repo private, pre-release.
- Đồng bộ tài khoản hoặc dữ liệu đám mây.
- Tự kiểm tra cập nhật chạy nền và thông báo nền.

## 3. Luồng lần đầu mở app

1. Hiển thị màn chào ngắn: “TV App Manager cần quyền cài đặt ứng dụng để cài và cập nhật APK.”
2. Người dùng bấm **Cho phép** bằng remote.
3. App mở màn hình Android Settings dành cho quyền “Install unknown apps” của chính TV App Manager.
4. Người dùng bật công tắc “Allow from this source”, sau đó bấm Back quay lại app.
5. App kiểm tra lại quyền.
6. Nếu đã có nguồn manifest, tải manifest và vào màn hình Cập nhật; nếu chưa có, hiển thị hướng dẫn mở menu ẩn bằng `8-8-0-0` để thêm nguồn manifest.

Nếu người dùng từ chối hoặc chưa bật quyền, app vẫn mở được nhưng vô hiệu hóa các nút cài/cập nhật và hiển thị hướng dẫn ngắn để cấp quyền lại.

## 4. Màn hình chính

Màn hình chính chỉ hiển thị những nội dung thường dùng. Không hiển thị “Nguồn” hoặc “Cài đặt” trên thanh điều hướng.

```text
TV App Manager                                               23:01

     [ ↑ CẬP NHẬT TẤT CẢ (2) ]   [ + CÀI TẤT CẢ (3) ]

                    Cập nhật       Đã cài đặt

Có bản cập nhật

[ Media Player TV ]                  [ File Browser ]
  1.4.2 -> 1.5.0                       2.0.1 -> 2.1.0
  Có bản mới                            Có bản mới

Đã mới nhất

[ Kodi ]                              [ Network Tools ]

Chưa cài đặt

[ TV Launcher ]                       [ System Cleaner ]
```

### Vùng hành động chính

Hai nút lớn nằm cố định ở phần đầu nội dung:

| Nút | Điều kiện chứa | Hành động |
|---|---|---|
| **Cập nhật tất cả (N)** | Package đã cài và version manifest lớn hơn version trên máy | Mở hộp thoại chọn N app để cập nhật |
| **Cài tất cả (N)** | Package có trong manifest nhưng chưa cài trên thiết bị | Mở hộp thoại chọn N app để cài |

- Nếu số lượng bằng 0, nút chuyển sang trạng thái mờ và không nhận focus.
- Người dùng đang đứng ở app nào cũng có thể nhấn `↑` liên tục để quay về **Cập nhật tất cả**.
- Từ nút **Cập nhật tất cả**, nhấn `→` đi đến **Cài tất cả**; nhấn `←` quay lại.
- Focus mặc định khi mở app và có cập nhật là **Cập nhật tất cả**.
- Sau khi hoàn thành/huỷ hàng đợi, focus quay về nút hành động này.

### Tab công khai

- **Cập nhật:** mặc định; hiển thị app có bản mới, app mới nhất, và app chưa cài theo các nhóm rõ ràng.
- **Đã cài đặt:** danh sách các app có trên TV; mở, gỡ app, xem package/version và tình trạng có bản mới trong manifest.

## 5. Điều hướng remote

- `↑ ↓ ← →`: di chuyển focus.
- `OK`/D-pad center: chọn, mở chi tiết hoặc xác nhận.
- `Back`: đóng hộp thoại, quay lại màn trước; tại màn hình gốc thì hỏi xác nhận thoát app.
- Chuỗi phím số `8`, `8`, `0`, `0` trong vòng 4 giây, tại màn hình gốc: mở menu **Cài đặt ẩn**.
- Chuỗi phím sai hoặc hết thời gian: xóa chuỗi nhập, không hiện thông báo để tránh gây rối.

### Quy tắc focus

- Mọi phần tử có thể thao tác đều focus được; focus có viền trắng dày, phóng to nhẹ và tương phản cao.
- Từ app ở hàng đầu, nhấn `↑` đi lên vùng nút hành động; app cần tự cuộn nếu cần.
- `↓` từ Cập nhật tất cả quay xuống app đầu tiên có bản mới.
- `↓` từ Cài tất cả quay xuống app đầu tiên trong nhóm Chưa cài đặt; UI tự cuộn tới nhóm này nếu nó chưa nằm trong vùng nhìn thấy.
- App ghi nhớ focus khi quay lại từ trang chi tiết hoặc từ hộp thoại.
- Không dựa chỉ vào màu để truyền trạng thái: mọi trạng thái luôn có chữ như “Có bản mới”, “Đã mới nhất”, “Chưa cài”.

## 6. Thẻ ứng dụng

Mỗi app hiển thị bằng thẻ lớn, dễ chọn bằng remote, theo lưới hai cột trên TV ngang.

- Icon 70 x 70 dp, tên app, version, và badge trạng thái.
- Không dùng bảng nhiều cột hoặc nút nhỏ kiểu giao diện web.
- Mỗi thẻ mở trang chi tiết: version thiết bị, version manifest, package name, dung lượng APK nếu biết, link tải bị rút gọn và nút Cài/Cập nhật/Mở/Gỡ.
- Nếu link icon tải lỗi hoặc manifest không có `iconUrl`, dùng icon mặc định của TV App Manager.
- Icon được cache local; màn hình không chờ tải icon mới hiển thị dữ liệu app.

## 7. Hàng đợi cài đặt

### Xác nhận trước khi chạy

Khi bấm một trong hai nút lớn, hiển thị hộp thoại đơn giản:

```text
CẬP NHẬT 2 ỨNG DỤNG?

[x] Media Player TV    1.4.2 -> 1.5.0
[x] File Browser       2.0.1 -> 2.1.0

[ HỦY ]                             [ BẮT ĐẦU ]
```

- Checkbox mặc định được chọn.
- Không cho bắt đầu khi không còn app nào được chọn.
- Trước khi bắt đầu, nhắc rõ Android TV sẽ hỏi xác nhận cài từng ứng dụng.

### Thực thi

1. Tạo hàng đợi theo lựa chọn của người dùng.
2. Tải APK qua HTTPS, hiển thị tiến độ, cho hủy và thử lại.
3. Nếu có `sha256`, kiểm tra checksum sau khi tải.
4. Đọc thông tin APK trước khi cài: package phải khớp trường `package`; version APK phải khớp hoặc lớn hơn version khai báo theo chính sách được định nghĩa.
5. Gọi PackageInstaller để mở xác nhận cài đặt của Android.
6. Sau khi người dùng xác nhận hoặc từ chối, chuyển sang mục tiếp theo.
7. Một app lỗi không làm dừng toàn bộ hàng đợi; báo cáo cuối gồm Thành công, Lỗi, Bỏ qua và nút Thử lại lỗi.

APK tạm được xóa sau khi cài theo cài đặt mặc định. Trong khi có hàng đợi, hai nút hành động chính đổi sang “Đang xử lý (x/y)” và mở màn hình tiến độ nếu được chọn.

## 8. Quản lý app đã cài

- Liệt kê app người dùng và app hệ thống (có bộ lọc).
- Hiển thị tên, icon, package name, versionName, versionCode và dung lượng nếu có.
- Hành động: Mở, xem thông tin hệ thống, gỡ cài đặt.
- Không cho gỡ TV App Manager; app hệ thống được đánh dấu rõ và có thể không gỡ được tùy thiết bị.
- Gỡ app luôn gọi UI xác nhận của Android; không hỗ trợ gỡ im lặng.

## 9. Menu cài đặt ẩn

Menu này không có trong tab hoặc màn hình chính. Nó dành cho người quản trị và được mở bằng `8-8-0-0`.

Các mục:

- **Nguồn manifest:** thêm, sửa, xóa, chọn nguồn đang hoạt động.
- **Nhập manifest từ file:** chọn JSON từ USB/bộ nhớ cục bộ.
- **Xuất cấu hình:** xuất các nguồn và tùy chọn, không xuất file APK tải về.
- **Quyền cài ứng dụng:** kiểm tra và mở màn hình Settings nếu quyền chưa có.
- **Mạng:** chỉ tải qua Wi-Fi/Ethernet, thời gian chờ tải, xóa cache.
- **Lưu trữ:** bật/tắt tự xóa APK sau cài; xem và dọn file tải tạm.
- **Ngôn ngữ:** Hệ thống, Tiếng Việt, English.
- **Chẩn đoán:** xem/export log lỗi.
- **Giới thiệu:** version TV App Manager.

Khi đóng menu ẩn, app trở lại màn hình trước đó và khôi phục focus.

## 10. Manifest JSON

Manifest là nguồn dữ liệu chính. App không gọi GitHub API; version và link tải được quản trị thủ công trong JSON.

### Schema v1

```json
{
  "schemaVersion": 1,
  "apps": [
    {
      "name": "Media Player TV",
      "package": "com.example.mediaplayer",
      "version": "1.5.0",
      "apkUrl": "https://github.com/owner/repo/releases/download/v1.5.0/media-player-tv.apk",
      "iconUrl": "https://raw.githubusercontent.com/owner/repo/main/icon.png",
      "sha256": "a1b2c3d4...",
      "versionMode": "semver"
    },
    {
      "name": "Network Tools",
      "package": "com.example.networktools",
      "version": "1.0.0",
      "apkUrl": "https://github.com/owner/repo/releases/download/v1.0.0/network-tools.apk",
      "versionMode": "semver"
    }
  ]
}
```

### Trường dữ liệu

| Trường | Bắt buộc | Ý nghĩa |
|---|---:|---|
| `schemaVersion` | Có | Phiên bản cấu trúc manifest, hiện là `1` |
| `apps` | Có | Danh sách ứng dụng |
| `name` | Có | Tên hiển thị |
| `package` | Có | Package name phải khớp APK và package trên TV |
| `version` | Có | Version mục tiêu để so sánh |
| `apkUrl` | Có | Link HTTPS trực tiếp tới APK |
| `iconUrl` | Không | Link HTTPS đến icon PNG/WebP; nếu thiếu/lỗi, dùng icon mặc định |
| `sha256` | Không | SHA-256 của APK để xác minh tính toàn vẹn |
| `versionMode` | Không | Mặc định `semver`; xác định cách so version |

### Quy tắc icon

- `iconUrl` là URL HTTPS công khai, khuyến nghị PNG hoặc WebP dạng vuông.
- Khuyến nghị kích thước ít nhất 256 x 256 px, tỉ lệ 1:1, dung lượng tối ưu dưới 500 KB.
- App tải icon nền, cache theo URL và dữ liệu cache được dọn khi người dùng xóa cache.
- Không có `iconUrl`, URL không hợp lệ, HTTP lỗi, hoặc định dạng ảnh lỗi: hiển thị icon chung của TV App Manager.
- Không dùng icon tải từ manifest để thay thế icon launcher của chính TV App Manager.

### So sánh phiên bản

- Mặc định `semver`: bỏ tiền tố `v`, tách các đoạn số, so theo từng đoạn. Ví dụ `1.5.0` lớn hơn `1.4.12`.
- Nếu parse không được, trạng thái là “Không xác định”; người dùng vẫn có thể cài/cập nhật thủ công từ trang chi tiết.
- Nên ưu tiên `versionCode` trong phiên bản schema sau để so sánh chính xác hơn. MVP dùng `version` như yêu cầu hiện tại.

## 11. Nguồn manifest

Mỗi nguồn gồm tên hiển thị, URL HTTPS của JSON hoặc URI file local, trạng thái hoạt động, thời điểm làm mới gần nhất và lỗi gần nhất.

- Manifest online phù hợp nhất là link raw GitHub, ví dụ `https://raw.githubusercontent.com/<owner>/<repo>/<branch>/apps.json`.
- App cache manifest thành công gần nhất để vẫn hiển thị được danh sách khi mất mạng.
- Nguồn chỉ được thêm/sửa/xóa trong menu cài đặt ẩn.
- MVP chỉ dùng một nguồn hoạt động tại một thời điểm để giao diện đơn giản.

## 12. Bảo mật và kiểm tra

- Chỉ chấp nhận URL `https` cho manifest, APK và icon.
- Kiểm tra package name của APK trước khi cài; từ chối nếu khác `package` trong JSON.
- Kiểm tra SHA-256 nếu manifest cung cấp.
- Bản cập nhật Android phải có chữ ký tương thích với app đang cài; nếu lỗi chữ ký, thông báo rõ người dùng có thể cần gỡ bản cũ trước khi cài.
- Kiểm tra dung lượng trống trước khi tải.
- Không lưu token GitHub vì MVP không dùng GitHub API.
- Lưu nguồn manifest và tùy chọn bằng DataStore; lưu log nội bộ và chỉ xuất log khi người dùng chọn trong menu ẩn.

## 13. Yêu cầu Android TV

- `minSdkVersion = 28`.
- Khai báo launcher Android TV/Leanback, banner 320 x 180, và `android.hardware.touchscreen` là không bắt buộc.
- Không yêu cầu chuột hoặc cảm ứng để thao tác các luồng chính.
- Vùng chạm/focus lớn, khoảng cách giữa thẻ đủ rộng; font lớn và tương phản cao.
- Giao diện cần an toàn với overscan, chừa khoảng đệm khoảng 5% mép màn hình.
- Hỗ trợ tiếng Việt và English; mặc định theo hệ thống, thay đổi trong menu ẩn.

## 14. Tiêu chí nghiệm thu MVP

1. Lần mở đầu hướng dẫn người dùng cấp quyền cài app và mở đúng trang Settings.
2. App tải và parse manifest JSON hợp lệ từ URL HTTPS hoặc file local.
3. App hiển thị đúng ba nhóm: Có bản cập nhật, Đã mới nhất, Chưa cài đặt.
4. `↑` liên tục từ các thẻ app đưa focus về Cập nhật tất cả; `→` chuyển sang Cài tất cả.
5. Hai nút tạo hàng đợi đúng loại app và cho phép bỏ chọn trước khi chạy.
6. APK tải về được kiểm tra package; checksum được kiểm tra khi tồn tại.
7. Cài đặt/gỡ cài đặt luôn gọi xác nhận Android, không tuyên bố hỗ trợ silent install.
8. Nhập `8-8-0-0` tại màn hình gốc mở menu cài đặt ẩn; menu không xuất hiện trên UI chính.
9. `iconUrl` hợp lệ hiển thị icon tương ứng; thiếu hoặc lỗi hiển thị icon chung.
10. Tất cả thao tác chính hoạt động với remote D-pad, OK, Back và phím số.
