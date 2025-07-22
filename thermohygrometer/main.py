#!/usr/bin/env python3
import time
import struct
import smbus2
import socket
from datetime import datetime
import pytz
from google.cloud import firestore

class AM2320:
    def __init__(self, bus_num=1, address=0x5C):
        self.bus = smbus2.SMBus(bus_num)
        self.address = address
    
    def read_data(self):
        try:
            # Wake up the sensor
            try:
                self.bus.write_i2c_block_data(self.address, 0x00, [])
            except:
                pass  # Expected to fail
            
            time.sleep(0.001)  # 1ms delay
            
            # Send read command for 4 bytes (humidity + temperature)
            self.bus.write_i2c_block_data(self.address, 0x03, [0x00, 0x04])
            time.sleep(0.0015)  # 1.5ms delay
            
            # Read 8 bytes (function code + data length + 4 data bytes + 2 CRC bytes)
            data = self.bus.read_i2c_block_data(self.address, 0, 8)
            
            # Verify CRC
            if self._crc16(data[:-2]) != struct.unpack('<H', bytes(data[-2:]))[0]:
                raise ValueError("CRC check failed")
            
            # Extract humidity and temperature
            humidity = struct.unpack('>H', bytes(data[2:4]))[0] / 10.0
            temperature = struct.unpack('>h', bytes(data[4:6]))[0] / 10.0
            
            return temperature, humidity
            
        except Exception as e:
            raise RuntimeError(f"Failed to read AM2320 sensor: {e}")
    
    def _crc16(self, data):
        crc = 0xFFFF
        for byte in data:
            crc ^= byte
            for _ in range(8):
                if crc & 0x0001:
                    crc >>= 1
                    crc ^= 0xA001
                else:
                    crc >>= 1
        return crc
    
    def close(self):
        """I2Cバスを閉じる"""
        self.bus.close()

def save_to_firestore(temperature, humidity, device_name=None):
    """温湿度データをFirestoreに保存"""
    try:
        # デバイス名を取得（指定されていない場合はホスト名を使用）
        if device_name is None:
            device_name = socket.gethostname()
        
        # Firestoreクライアントを初期化
        db = firestore.Client()
        
        # 日本時間で現在時刻を取得
        jst = pytz.timezone('Asia/Tokyo')
        now_jst = datetime.now(jst)
        
        # データを準備
        doc_data = {
            'device_name': device_name,
            'timestamp': now_jst,
            'temperature': temperature,
            'humidity': humidity
        }
        
        # データを保存
        doc_ref = db.collection('thermohygrometer').add(doc_data)
        print(f"データを保存しました: {doc_ref[1].id}")
        return True
        
    except Exception as e:
        print(f"Firestore保存エラー: {e}")
        return False

def main():
    sensor = AM2320()
    max_retries = 3
    retry_delay = 1.0  # seconds
    
    for attempt in range(max_retries):
        try:
            # センサーからデータを読み取り
            temperature, humidity = sensor.read_data()
            print(f"読み取り完了 - 温度: {temperature:.1f}°C, 湿度: {humidity:.1f}%")
            
            # Firestoreにデータを保存
            if save_to_firestore(temperature, humidity):
                print("データの保存が完了しました")
            else:
                print("データの保存に失敗しました")
            
            # 成功した場合はループを抜ける
            break
            
        except Exception as e:
            attempt_msg = f"試行 {attempt + 1}/{max_retries}"
            print(f"エラー ({attempt_msg}): {e}")
            
            # 最後の試行でない場合は待機してリトライ
            if attempt < max_retries - 1:
                print(f"{retry_delay}秒後にリトライします...")
                time.sleep(retry_delay)
            else:
                print("全ての試行が失敗しました")
                raise

if __name__ == "__main__":
    main()