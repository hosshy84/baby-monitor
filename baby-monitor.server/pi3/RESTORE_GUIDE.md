# Raspberry Pi 3 Model B リストア手順

## 前提
- Raspberry Pi OS (Legacy, 32-bit) をクリーンインストール済み
- ユーザー名: `tatsuya`
- ホスト名: `raspberrypi3`
- 接続: `ssh pi3`（~/.ssh/config のエイリアスを使用）
- 作業は SSH またはローカルターミナルで実施

---

## 1. /boot/config.txt の設定

```bash
sudo nano /boot/config.txt
```

以下を追加・確認（既存行はコメントアウトを外す）:

```ini
dtparam=i2c_arm=on       # I2C有効（温湿度センサー用）
dtparam=audio=on         # オーディオ有効（USBマイク用）
start_x=1                # カメラモジュール有効
gpu_mem=128
```

### /boot/cmdline.txt

末尾に `cma=128M` を追加（スペース区切り、改行しない）:

```
console=serial0,115200 console=tty1 root=PARTUUID=xxxxxxxx-02 rootfstype=ext4 fsck.repair=yes rootwait quiet splash plymouth.ignore-serial-consoles cma=128M
```

> **注意**: `PARTUUID` はクリーンインストール時の値をそのまま使うこと（書き換えない）

設定後に再起動:

```bash
sudo reboot
```

---

## 2. ネットワーク設定（静的IP）

```bash
sudo nano /etc/dhcpcd.conf
```

末尾に追加:

```
interface wlan0
static ip_address=192.168.68.18/22
static routers=192.168.68.1
static domain_name_servers=192.168.68.1
```

### Wi-Fi 設定

```bash
sudo nano /etc/wpa_supplicant/wpa_supplicant.conf
```

`raspi_extracted/wpa_supplicant.conf` の内容で上書き（パスワードハッシュ済みのためそのままコピー可）:

```
ctrl_interface=DIR=/var/run/wpa_supplicant GROUP=netdev
update_config=1

network={
    ssid="Tatsuya-LAN"
    psk=c27ed870cab63e4754d02b0a3ae90492a1f4caf3984afa10a839322517aa6a6d
}
```

---

## 3. ユーザー設定（グループ追加）

クリーンインストール時に `tatsuya` ユーザーを作成した場合、以下のグループへ追加:

```bash
sudo usermod -aG adm,dialout,cdrom,sudo,audio,video,plugdev,games,users,input,render,netdev,spi,i2c,gpio,lpadmin tatsuya
```

反映確認:
```bash
groups tatsuya
```

---

## 4. 依存パッケージのインストール

```bash
sudo apt-get update
sudo apt-get install -y \
    ffmpeg \
    pulseaudio \
    pulseaudio-utils \
    v4l-utils \
    python3 \
    python3-pip \
    python3-venv \
    i2c-tools
```

---

## 5. MediaMTX（RTSPサーバー）

### Mac から Pi へファイルを転送

```bash
cd /Users/tatsuya/Documents/raspi_extracted

ssh pi3 'sudo mkdir -p /opt/mediamtx'
scp mediamtx                  pi3:/tmp/mediamtx
scp mediamtx.yml              pi3:/tmp/mediamtx.yml
scp mediamtx_stream_script.sh pi3:/tmp/mediamtx_stream_script.sh
scp mediamtx.service          pi3:/tmp/mediamtx.service

ssh pi3 '
  sudo mv /tmp/mediamtx                  /opt/mediamtx/mediamtx
  sudo mv /tmp/mediamtx.yml              /opt/mediamtx/mediamtx.yml
  sudo mv /tmp/mediamtx_stream_script.sh /opt/mediamtx/mediamtx_stream_script.sh
  sudo chmod +x /opt/mediamtx/mediamtx /opt/mediamtx/mediamtx_stream_script.sh
  sudo chown -R tatsuya:tatsuya /opt/mediamtx
  sudo mv /tmp/mediamtx.service /etc/systemd/system/mediamtx.service
'
```

### サービスの有効化

```bash
ssh pi3 'sudo systemctl daemon-reload && sudo systemctl enable mediamtx.service'
```

### mediamtx.yml の確認事項

- 認証情報（`authInternalUsers`）にユーザー名・パスワードが記載されているため、必要に応じて変更
- `stream` パスの `runOnInit` が `/opt/mediamtx/mediamtx_stream_script.sh` を指していることを確認

---

## 6. カメラ映像ストリーミング（picam-vid）

### 注意: picam-vid.sh が見つからない

`picam-vid.service` は `/opt/camera-stream/picam-vid.sh` を参照しているが、
バックアップイメージ内にこのファイルが存在しない。

実際のストリーミングは `mediamtx_stream_script.sh`（MediaMTX の `runOnInit`）が担っているため、
`picam-vid.service` は不要の可能性が高い。

**推奨**: MediaMTX の `runOnInit` 経由でストリーミングするため、`picam-vid.service` は登録しない。
動作確認後に必要であれば改めてスクリプトを作成する。

もし登録する場合:
```bash
sudo mkdir -p /opt/camera-stream
# picam-vid.sh を新規作成（mediamtx_stream_script.sh を参考に）
sudo cp picam-vid.service /etc/systemd/system/picam-vid.service
sudo systemctl daemon-reload
sudo systemctl enable picam-vid.service
```

---

## 7. Baby Monitor（mjpg-streamer）

### 注意: mediamtx と同時使用不可

`baby-monitor.service` と `picam-vid.service（mediamtx）` は同じカメラデバイス（/dev/video0）を使用するため、同時に有効化できない。どちらか一方を選択すること。

### mjpg-streamer のビルドと配置

```bash
sudo apt-get install -y cmake libjpeg-dev
mkdir -p ~/Documents/mjpg-streamer
cd ~/Documents/mjpg-streamer
git clone https://github.com/jacksonliam/mjpg-streamer.git .
cd mjpg-streamer-experimental
make
```

### Mac から Pi へファイルを転送・有効化

```bash
cd /Users/tatsuya/Documents/raspi_extracted
scp baby-monitor.service pi3:/tmp/
ssh pi3 '
  sudo mv /tmp/baby-monitor.service /etc/systemd/system/baby-monitor.service
  sudo systemctl daemon-reload
  sudo systemctl enable baby-monitor.service
'
```

---

## 8. 温湿度センサー（thermohygrometer + cron）

### Mac から Pi へファイルを転送

```bash
cd /Users/tatsuya/Documents/raspi_extracted

ssh pi3 'sudo mkdir -p /opt/thermohygrometer && sudo chown tatsuya:tatsuya /opt/thermohygrometer'
scp main.py requirements.txt export-env \
    baby-monitor-407012-firebase-adminsdk-vg0gg-eb32ffefe9.json \
    pi3:/opt/thermohygrometer/
```

### Python 仮想環境の構築

```bash
cd /opt/thermohygrometer
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
deactivate
```

### I2C の動作確認

```bash
sudo i2cdetect -y 1
# アドレス 0x5C に AM2320 センサーが表示されることを確認
```

### crontab の設定

```bash
crontab -e
```

以下を追加:

```
SHELL=/bin/bash
* * * * * cd /opt/thermohygrometer && source export-env && .venv/bin/python3 main.py
```

動作確認:
```bash
cd /opt/thermohygrometer && source export-env && .venv/bin/python3 main.py
```

---

## 9. 動作確認

```bash
# サービス状態確認
sudo systemctl status mediamtx.service
sudo systemctl status baby-monitor.service   # または picam-vid.service

# ログ確認
journalctl -u mediamtx.service -f
journalctl -u baby-monitor.service -f

# cron 確認（毎分実行されるので1分待つ）
grep CRON /var/log/syslog | tail -5
```

---

## 作業チェックリスト

- [ ] /boot/config.txt の設定（i2c, audio, camera, gpu_mem）
- [ ] /boot/cmdline.txt に `cma=128M` 追加
- [ ] 静的IP設定（192.168.68.18/22）
- [ ] Wi-Fi設定（Tatsuya-LAN）
- [ ] ユーザーグループ追加（i2c, audio, video, gpio 等）
- [ ] 依存パッケージインストール（ffmpeg, pulseaudio, python3 等）
- [ ] MediaMTX バイナリ・設定・スクリプト配置
- [ ] mediamtx.service 登録・有効化
- [ ] baby-monitor または picam-vid どちらを使うか決定・設定
- [ ] thermohygrometer ファイル配置・venv構築
- [ ] crontab 設定
- [ ] 再起動後に全サービス動作確認
