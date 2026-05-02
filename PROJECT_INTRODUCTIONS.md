之前，我苦于手机系统在同时听几个asmr音频的时候音量不能单独调整，询问DeepSeek之后，它帮我找到了SoundAura项目，然后我基于1.6.2的SoundAura让GPT按照我的想法添加了一些功能，所有的代码均由GPT编写哦。

# Mistysound Project Introductions

These introductions are written in the style of the original SoundAura README, with extra notes about the changes added in Mistysound compared with SoundAura 1.6.2.

## 中文

# Mistysound

Mistysound 是基于 SoundAura 1.6.2 修改而来的开源环境音应用。它本身不内置音频文件，以减少安装包体积；用户可以从手机本地添加音频，同时播放多个音频，并分别调整每个音频的音量。Mistysound 也保留了 SoundAura 的音频焦点模式和后台播放模式，可以根据需要选择是否让它与其他应用一起播放声音。

## 相比 SoundAura 1.6.2 的主要更新

- 更名为 Mistysound，并更换了应用名称和图标。
- 为每个音频项目增加独立的播放进度条和 0.1x 到 5x 播放速度设置。
- 新增音频文件夹功能：可以从本地音频或当前选中的音频创建文件夹，向文件夹继续添加音频，长按排序，并让文件夹按顺序或随机方式播放。
- 优化文件夹打开体验，文件夹页面可以更快打开，内容逐步载入，并修复了文件夹内容加载期间可能出现的崩溃。
- 新增主背景自定义功能：可以导入图片、预览、重命名、排序、启用或禁用、删除，并使用裁剪编辑器调整显示区域。
- 搜索逻辑改为跟随当前页面：主页媒体库、文件夹页面和主背景页面都可以独立搜索各自内容。
- 新增浅色和深色毛玻璃主题，并提供 0-100% 的卡片透明度滑块。
- 优化毛玻璃风格下的按钮、滑块、播放控制栏和顶部栏，让自定义背景在更多界面中可见。
- 新增应用内语言设置，并加入中文、英文、韩语、日语等界面文本。
- 区分个人版和公开版发布图标，方便同时安装或测试时识别。

## 功能

- 从手机本地文件添加音频、播放列表和文件夹，并让多个音频同时播放。
- 为每个音频或播放列表单独调节音量，也可以使用音量增强。
- 为每个音频项目调整播放进度和播放速度。
- 创建用户预设，保存常用的声音组合。
- 长按播放/暂停按钮设置自动停止计时器。
- 使用媒体通知或快速设置磁贴控制后台播放。
- 支持通话期间自动暂停，以及切换到零音量音频设备时自动暂停或停止。
- 自定义主界面背景，搭配浅色、深色或毛玻璃主题使用。
- 不强制要求权限；只有在添加大量文件、显示后台播放通知或启用通话自动暂停等场景中，才会按 Android 版本请求相应权限。

## 隐私政策

Mistysound 不会收集、存储或传输任何个人信息。

## 许可证与来源

Mistysound 基于 SoundAura 1.6.2 修改，源代码按照 Apache License 2.0 条款发布。原项目地址：https://github.com/CliffracerMerchant/SoundAura

## English

# Mistysound

Mistysound is an open source ambient sound app based on SoundAura 1.6.2. It does not include audio tracks, keeping the app package small; instead, users add local audio files from their device, play multiple sounds at the same time, and control each audio item's volume separately. Mistysound also keeps SoundAura's audio-focus-respecting mode and play-in-background mode, so it can either cooperate with other audio apps or play alongside them.

## Main Changes Compared With SoundAura 1.6.2

- Renamed the app experience to Mistysound with a new app name and launcher icon.
- Added per-audio playback progress controls and playback speed settings from 0.1x to 5x.
- Added audio folders: create folders from local audio or currently selected library items, add more audio later, reorder contents with a long press, and play folders sequentially or randomly.
- Improved folder loading so folder pages open faster, fill in progressively, and avoid crashes while contents are still loading.
- Added custom main backgrounds, with image import, preview, rename, reorder, enable or disable, remove, and crop tools.
- Made search page-aware, so the library, folder view, and main background page each search their own content independently.
- Added dark and light frosted glass themes with a 0-100% card opacity slider.
- Refined frosted glass buttons, sliders, playback controls, and the top app bar so custom backgrounds remain visible in more places.
- Added in-app language selection and interface text for Chinese, English, Korean, and Japanese.
- Added separate launcher icons for personal and public release builds.

## Features

- Add local audio files, playlists, and folders from the device, then play multiple audio items at once.
- Control each track or playlist's volume separately, with optional volume boost.
- Adjust each audio item's playback progress and playback speed.
- Create user presets for favorite sound mixes.
- Set an automatic stop timer by long-pressing the play/pause button.
- Control background playback from a media notification or quick settings tile.
- Auto-pause during calls, and automatically pause or stop when switching to an audio device with zero volume.
- Customize the main page background, then pair it with light, dark, or frosted glass themes.
- No permissions are strictly required; Android permissions are requested only when needed for large media libraries, background playback notifications, or call-state auto-pause.

## Privacy Policy

Mistysound does not collect, store, or transmit any personal information.

## License and Source

Mistysound is based on SoundAura 1.6.2 and is released under the Apache License 2.0. Original project: https://github.com/CliffracerMerchant/SoundAura

## 한국어

# Mistysound

Mistysound는 SoundAura 1.6.2를 기반으로 수정한 오픈 소스 환경음 앱입니다. 앱 용량을 줄이기 위해 오디오 파일을 기본으로 포함하지 않으며, 사용자가 기기 안의 로컬 오디오를 직접 추가해 여러 소리를 동시에 재생하고 각 오디오의 볼륨을 따로 조절할 수 있습니다. 또한 SoundAura의 오디오 포커스 준수 모드와 백그라운드 재생 모드를 유지하여, 다른 앱의 소리와 조화롭게 재생하거나 함께 재생되도록 선택할 수 있습니다.

## SoundAura 1.6.2와 비교한 주요 변경 사항

- 앱 경험을 Mistysound로 변경하고 새로운 앱 이름과 실행 아이콘을 적용했습니다.
- 각 오디오 항목에 개별 재생 진행 슬라이더와 0.1x부터 5x까지의 재생 속도 설정을 추가했습니다.
- 오디오 폴더 기능을 추가했습니다. 로컬 오디오나 현재 선택한 라이브러리 항목으로 폴더를 만들고, 나중에 오디오를 더 추가하고, 길게 눌러 순서를 바꾸며, 순차 재생 또는 무작위 재생을 선택할 수 있습니다.
- 폴더 페이지가 더 빠르게 열리고 내용이 점진적으로 표시되도록 개선했으며, 내용 로딩 중 발생할 수 있는 충돌을 수정했습니다.
- 메인 배경을 직접 꾸밀 수 있게 했습니다. 이미지를 가져오고, 미리 보고, 이름을 바꾸고, 순서를 정하고, 켜거나 끄고, 삭제하고, 자를 수 있습니다.
- 검색이 현재 페이지를 따르도록 개선되어 라이브러리, 폴더 보기, 메인 배경 페이지가 각각 자신의 내용만 검색합니다.
- 어두운/밝은 프로스트 글래스 테마와 0-100% 카드 투명도 슬라이더를 추가했습니다.
- 프로스트 글래스 테마의 버튼, 슬라이더, 재생 컨트롤, 상단 앱 바를 다듬어 사용자 배경이 더 많은 화면에서 잘 보이도록 했습니다.
- 앱 안에서 언어를 선택할 수 있게 하고 중국어, 영어, 한국어, 일본어 인터페이스 텍스트를 추가했습니다.
- 개인용 빌드와 공개 빌드의 실행 아이콘을 구분했습니다.

## 기능

- 기기의 로컬 오디오, 재생목록, 폴더를 추가하고 여러 오디오 항목을 동시에 재생할 수 있습니다.
- 각 트랙이나 재생목록의 볼륨을 따로 조절하고, 필요하면 볼륨 부스트를 사용할 수 있습니다.
- 각 오디오 항목의 재생 위치와 재생 속도를 조절할 수 있습니다.
- 자주 쓰는 사운드 조합을 사용자 프리셋으로 저장할 수 있습니다.
- 재생/일시정지 버튼을 길게 눌러 자동 정지 타이머를 설정할 수 있습니다.
- 미디어 알림이나 빠른 설정 타일로 백그라운드 재생을 제어할 수 있습니다.
- 통화 중 자동 일시정지, 그리고 볼륨이 0인 오디오 장치로 전환될 때 자동 일시정지 또는 정지를 지원합니다.
- 메인 화면 배경을 직접 꾸미고, 밝은 테마, 어두운 테마, 프로스트 글래스 테마와 함께 사용할 수 있습니다.
- 필수 권한은 없습니다. 대량의 미디어를 추가하거나 백그라운드 재생 알림을 표시하거나 통화 상태 기반 자동 일시정지를 사용할 때만 Android 권한을 요청합니다.

## 개인정보 보호

Mistysound는 개인정보를 수집, 저장 또는 전송하지 않습니다.

## 라이선스와 출처

Mistysound는 SoundAura 1.6.2를 기반으로 하며 Apache License 2.0 조건에 따라 배포됩니다. 원본 프로젝트: https://github.com/CliffracerMerchant/SoundAura

## 日本語

# Mistysound

Mistysound は SoundAura 1.6.2 をもとに変更したオープンソースの環境音アプリです。アプリ本体には音声ファイルを含めず、パッケージサイズを小さくしています。ユーザーは端末内のローカル音声を追加し、複数の音声を同時に再生しながら、それぞれの音量を個別に調整できます。SoundAura のオーディオフォーカスを尊重するモードとバックグラウンド再生モードも引き継いでおり、他のアプリの音声と調和させるか、同時に鳴らすかを選べます。

## SoundAura 1.6.2 からの主な変更点

- アプリ体験を Mistysound として刷新し、新しいアプリ名とランチャーアイコンを適用しました。
- 各音声項目に個別の再生位置スライダーと、0.1x から 5x までの再生速度設定を追加しました。
- 音声フォルダー機能を追加しました。ローカル音声や現在選択中のライブラリ項目からフォルダーを作成し、後から音声を追加し、長押しで並べ替え、順番再生またはランダム再生を選べます。
- フォルダーページがすぐに開き、内容が段階的に表示されるよう改善し、読み込み中に発生する可能性のあるクラッシュも修正しました。
- メイン背景をカスタマイズできるようにしました。画像のインポート、プレビュー、名前変更、並べ替え、有効化/無効化、削除、切り抜きに対応しています。
- 検索が現在のページに連動するようになり、ライブラリ、フォルダー表示、メイン背景ページがそれぞれ自分の内容を検索します。
- ダーク/ライトのフロストガラステーマと、0-100% のカード透明度スライダーを追加しました。
- フロストガラステーマのボタン、スライダー、再生コントロール、トップアプリバーを調整し、カスタム背景がより多くの画面で見えるようにしました。
- アプリ内の言語選択を追加し、中国語、英語、韓国語、日本語の UI テキストを用意しました。
- 個人用ビルドと公開ビルドで別々のランチャーアイコンを使うようにしました。

## 機能

- 端末内のローカル音声、プレイリスト、フォルダーを追加し、複数の音声項目を同時に再生できます。
- 各トラックまたはプレイリストの音量を個別に調整でき、必要に応じて音量ブーストも使えます。
- 各音声項目の再生位置と再生速度を調整できます。
- よく使うサウンドミックスをユーザープリセットとして保存できます。
- 再生/一時停止ボタンを長押しして、自動停止タイマーを設定できます。
- メディア通知やクイック設定タイルからバックグラウンド再生を操作できます。
- 通話中の自動一時停止や、音量が 0 のオーディオデバイスへ切り替わったときの自動一時停止/停止に対応しています。
- メイン画面の背景をカスタマイズし、ライト、ダーク、フロストガラステーマと組み合わせて使えます。
- 必須権限はありません。大量のメディア追加、バックグラウンド再生通知、通話状態による自動一時停止など、必要な場面でのみ Android 権限を要求します。

## プライバシーポリシー

Mistysound は個人情報を収集、保存、送信しません。

## ライセンスと出典

Mistysound は SoundAura 1.6.2 をベースにしており、Apache License 2.0 の条件で公開されています。原プロジェクト: https://github.com/CliffracerMerchant/SoundAura
