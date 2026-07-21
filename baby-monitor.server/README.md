# baby-monitor サーバー（Raspberry Pi）構成

Raspberry Pi 上で [mediamtx](https://github.com/bluenviron/mediamtx) を使い、
映像+音声の RTSP ストリーム `/stream` を配信するための設定ファイル一式。

| 機体 | ホスト名 | SSH | カメラ | オーディオ |
|------|---------|-----|--------|-----------|
| pi3  | raspberrypi3 | `ssh pi3` | USBカメラ `/dev/video0` | USBマイク（C-Media USB PnP） |
| pi5  | raspberrypi5 | `ssh pi5` | rpiCamera（公式カメラ） | USBマイク（C-Media USB PnP） |

各機のディレクトリ構成:

```
pi3/mediamtx/   pi5/mediamtx/
├── mediamtx.service          # systemd ユニット
├── mediamtx.yml              # mediamtx 設定（★パスワードは空欄。後述）
└── mediamtx_stream_script.sh # /stream を組み立てる起動スクリプト
```

## ストリーミングの仕組み

どちらも mediamtx の `stream:` パスで `runOnInit` に起動スクリプトを指定し、
そのスクリプトが `/stream` を配信する。

- **pi3**: `ffmpeg` で USBカメラ `/dev/video0` の映像を `h264_v4l2m2m`（ハードウェア
  エンコード）し、USBマイクの音声（AAC）と合わせて `/stream` へ配信。
- **pi5**: `gst-launch` で mediamtx 内部の `/video`（rpiCamera の H264）を取り込み、
  USBマイクの音声（AAC）を合成して `/stream` へ配信。

### オーディオデバイスの動的検出

以前は特定のマイクデバイスに依存していた（pi3 は PulseAudio の長いソース名を
ハードコード、pi5 は `alsasrc device=hw:2,0` とカード番号を固定）。USBオーディオ機器の
交換や認識順の変化で壊れるため、**デバイスを動的検出する方式に統一**した。

- **pi3**: `pactl list sources short` から `alsa_input`（`.monitor` を除く）を検出。
- **pi5**: `arecord -l` から USB オーディオのカード番号を検出して `hw:X,0` を組み立て。

検出できない場合はスクリプトが明示エラーで停止する（`set -euo pipefail`）。

## ⚠️ パスワードの扱い（重要）

**`mediamtx.yml` に含まれる `authInternalUsers` のパスワードは、このリポジトリでは
意図的に空欄（`pass:`）にしてある。** 認証情報を Git にコミットしないための措置。

このリポジトリからリストアした場合は、mediamtx を起動する前に実機の
`/opt/mediamtx/mediamtx.yml` で各ユーザー（`tatsuya` / `izumi` / `admin`）の
`pass:` に実際のパスワードを設定すること。

```yaml
authInternalUsers:
- user: tatsuya
  pass:            # ← ここに実パスワードを入れる
  ...
- user: izumi
  pass:            # ← ここに実パスワードを入れる
  ...
- user: admin
  pass:            # ← ここに実パスワードを入れる
  ips: ['127.0.0.1', '::1']
```

> なお `mediamtx_stream_script.sh` 内の配信先 RTSP URL には localhost 向けの
> 認証情報が埋め込まれている（`rtsp://tatsuya:...@127.0.0.1:8554/stream`）。
> これはローカルループバック接続専用。パスワードを変更した場合はスクリプト側の
> URL も合わせて更新すること。

## デプロイ / リストア手順

```sh
# 例: pi5（pi3 も同様。piX を読み替える）
scp pi5/mediamtx/mediamtx.service         pi5:/tmp/
scp pi5/mediamtx/mediamtx_stream_script.sh pi5:/opt/mediamtx/mediamtx_stream_script.sh
scp pi5/mediamtx/mediamtx.yml             pi5:/opt/mediamtx/mediamtx.yml

ssh pi5 '
  sudo mv /tmp/mediamtx.service /etc/systemd/system/mediamtx.service
  chmod +x /opt/mediamtx/mediamtx_stream_script.sh
  # ★ /opt/mediamtx/mediamtx.yml の pass: に実パスワードを設定してから↓
  sudo systemctl daemon-reload
  sudo systemctl enable --now mediamtx
'
```

- pi3 の `sudo` はパスワードが必要（NOPASSWD 不可）。`ssh -t pi3 ...` で TTY を付けるか
  手動で実行する。
- pi5 は NOPASSWD で `systemctl restart` 可。

## 動作確認

```sh
ssh piX "systemctl is-active mediamtx"                       # → active
ssh piX "ps aux | grep -E '[f]fmpeg|[g]st-launch'"           # 配信プロセスが動いているか
# /stream に映像(h264)+音声(aac)の2トラックが流れているか（実パスワードで）
ssh piX "ffprobe -v error -rtsp_transport tcp \
  -show_entries stream=index,codec_type,codec_name -of csv \
  'rtsp://tatsuya:<pass>@127.0.0.1:8554/stream'"
# → h264,video と aac,audio の2行が出れば正常
```

## 注意点

- mediamtx の HTTP API (9997) は両機とも無効。path 状態は `ffprobe` で確認する。
- `raspberrypi3.local` は mDNS の初回解決が遅くタイムアウトしやすい。数回リトライする。
