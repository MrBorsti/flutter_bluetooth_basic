// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'bluetooth_device.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

BluetoothDevice _$BluetoothDeviceFromJson(Map<String, dynamic> json) =>
    BluetoothDevice(
      name: json['name'] as String,
      address: json['address'] as String,
    )
      ..type = json['type'] as int
      ..connected = json['connected'] as bool? ?? false;

Map<String, dynamic> _$BluetoothDeviceToJson(BluetoothDevice instance) =>
    <String, dynamic>{
      'name': instance.name,
      'address': instance.address,
      'type': instance.type,
      'connected': instance.connected,
    };

LineText _$LineTextFromJson(Map<String, dynamic> json) => LineText(
      type: json['type'] as String,
      content: json['content'] as String,
      size: json['size'] as int? ?? 0,
      align: json['align'] as int? ?? LineText.ALIGN_LEFT,
      weight: json['weight'] as int? ?? 0,
      width: json['width'] as int? ?? 0,
      height: json['height'] as int? ?? 0,
      underline: json['underline'] as int? ?? 0,
      linefeed: json['linefeed'] as int? ?? 0,
      x: json['x'] as int? ?? 0,
      y: json['y'] as int? ?? 0,
    );

Map<String, dynamic> _$LineTextToJson(LineText instance) => <String, dynamic>{
      'type': instance.type,
      'content': instance.content,
      'size': instance.size,
      'align': instance.align,
      'weight': instance.weight,
      'width': instance.width,
      'height': instance.height,
      'underline': instance.underline,
      'linefeed': instance.linefeed,
      'x': instance.x,
      'y': instance.y,
    };
