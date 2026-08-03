# RX400h 协议探针日志规范 v0.1

## 原始通信记录 `raw_io.jsonl`
每行记录一次完整命令事务：

- `session_id`
- `monotonic_ns`
- `wall_time_iso`
- `adapter_name`
- `adapter_address`
- `ecu_session_candidate`
- `tx_header`
- `command_sent`
- `payload`
- `expected_response_count`
- `raw_response_lines[]`
- `normalized_hex`
- `latency_ms`
- `status`: `OK | TIMEOUT | NEGATIVE_RESPONSE | PARSE_ERROR | DISCONNECTED`
- `retry_index`

## 解码候选记录 `decoded.jsonl`
每一项解码结果必须保留可追溯信息：

- `signal_name`
- `value`
- `unit`
- `valid`
- `confidence`
- `source_header`
- `source_request`
- `source_response_prefix`
- `source_byte_start`
- `source_byte_length`
- `source_bytes_hex`
- `decoder_id`
- `decoder_version`
- `formula`
- `updated_monotonic_ns`
- `age_ms`

## 统一帧 `frames.csv`
只用于方便分析；原始日志始终是事实底稿。

核心列：

- `timestamp_ms`
- `speed_kph_std`
- `rpm_std`
- `coolant_c_std`
- `adapter_12v_v`
- `soc_candidate`
- `hv_voltage_candidate_v`
- `hv_current_candidate_a`
- `hv_power_candidate_kw`
- `temp1_c` ... `temp6_c`
- `room_candidate_c`
- `temp_max_c`
- `temp_min_c`
- `temp_avg_c`
- `temp_hot3_avg_c`
- `temp_delta_c`
- `data_freshness_mask`

## 事件标记 `events.csv`

- `timestamp_ms`
- `event_type`
- `note`

## 分析原则

1. 任何候选公式错误时，必须能从 `raw_io.jsonl` 离线重算。
2. 不因 UI 显示而丢弃原始字节。
3. 同一数据允许并行存在多个候选解码器。
4. 未确认的 ECU Header 不写死为事实，只标记为候选。
5. 正式版只继承经过实车日志验证的解析路径。
