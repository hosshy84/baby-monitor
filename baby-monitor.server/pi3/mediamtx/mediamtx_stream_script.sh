#!/bin/bash

# PulseAudioサービスが動作しているかチェック
if ! pulseaudio --check; then
    echo "PulseAudio is not running"
    exit 1
fi

# USB音声デバイス（正しいソース名）をアクティブにする
pactl suspend-source alsa_input.usb-C-Media_Electronics_Inc._USB_PnP_Sound_Device-00.mono-fallback false 2>/dev/null || true
sleep 1

# 音量調整（正しいソース名）
pactl set-source-volume alsa_input.usb-C-Media_Electronics_Inc._USB_PnP_Sound_Device-00.mono-fallback 90% 2>/dev/null || true

# デバイスが利用可能になるまで待機
for i in {1..5}; do
    if pactl list sources short | grep -q "alsa_input.usb-C-Media.*RUNNING\|alsa_input.usb-C-Media.*IDLE"; then
        break
    fi
    sleep 1
done

# ハードウェアエンコード + PulseAudio正しいソース名指定
exec ffmpeg \
    -thread_queue_size 1024 \
    -f v4l2 -video_size 1366x768 -framerate 15 -i /dev/video0 \
    -f pulse -ac 1 -ar 44100 -i alsa_input.usb-C-Media_Electronics_Inc._USB_PnP_Sound_Device-00.mono-fallback \
    -c:v h264_v4l2m2m -bf 0 -b:v 1.5M -maxrate 1.5M -bufsize 3M -g 30 -pix_fmt yuv420p \
    -c:a aac -b:a 128k -ar 44100 -ac 1 \
    -async 1 -vsync cfr \
    -rtsp_transport tcp \
    -f rtsp rtsp://tatsuya:MG4Smd2+O@localhost:8554/stream
