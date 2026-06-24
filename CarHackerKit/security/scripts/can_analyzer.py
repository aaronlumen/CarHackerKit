#!/usr/bin/env python3
"""
CAN Bus Traffic Analyzer
From "The Car Hacker's Handbook" techniques

Analyzes captured CAN traffic to identify:
- Active arbitration IDs
- Message patterns and frequencies
- Potential sensor values
- Anomalies and security issues
"""

import sys
import json
import struct
from collections import defaultdict
from datetime import datetime
from typing import Dict, List, Tuple, Optional
import argparse


class CANMessage:
    """Represents a single CAN message"""
    
    def __init__(self, timestamp: float, arb_id: int, data: bytes):
        self.timestamp = timestamp
        self.arb_id = arb_id
        self.data = data
    
    def __repr__(self):
        data_hex = ' '.join(f'{b:02X}' for b in self.data)
        return f"{self.timestamp:.6f} {self.arb_id:03X} [{len(self.data)}] {data_hex}"


class CANAnalyzer:
    """Analyzes CAN bus traffic for security research"""
    
    def __init__(self):
        self.messages: List[CANMessage] = []
        self.arb_id_stats: Dict[int, dict] = defaultdict(lambda: {
            'count': 0,
            'first_seen': None,
            'last_seen': None,
            'intervals': [],
            'data_samples': [],
            'byte_ranges': [set() for _ in range(8)]
        })
    
    def load_candump(self, filename: str):
        """Load candump format: (timestamp) interface arbid#data"""
        with open(filename, 'r') as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith('#'):
                    continue
                
                try:
                    parts = line.split()
                    timestamp = float(parts[0].strip('()'))
                    can_data = parts[2].split('#')
                    arb_id = int(can_data[0], 16)
                    data = bytes.fromhex(can_data[1])
                    
                    msg = CANMessage(timestamp, arb_id, data)
                    self.messages.append(msg)
                    self._update_stats(msg)
                except (ValueError, IndexError):
                    continue
    
    def load_csv(self, filename: str):
        """Load CSV format: timestamp,arb_id,data_hex"""
        with open(filename, 'r') as f:
            header = f.readline()
            for line in f:
                parts = line.strip().split(',')
                if len(parts) >= 3:
                    try:
                        timestamp = float(parts[0])
                        arb_id = int(parts[1], 16) if parts[1].startswith('0x') else int(parts[1])
                        data = bytes.fromhex(parts[2].replace(' ', ''))
                        
                        msg = CANMessage(timestamp, arb_id, data)
                        self.messages.append(msg)
                        self._update_stats(msg)
                    except ValueError:
                        continue
    
    def _update_stats(self, msg: CANMessage):
        """Update statistics for an arbitration ID"""
        stats = self.arb_id_stats[msg.arb_id]
        
        if stats['first_seen'] is None:
            stats['first_seen'] = msg.timestamp
        else:
            interval = msg.timestamp - stats['last_seen']
            stats['intervals'].append(interval)
        
        stats['last_seen'] = msg.timestamp
        stats['count'] += 1
        
        if len(stats['data_samples']) < 1000:
            stats['data_samples'].append(msg.data)
        
        for i, byte in enumerate(msg.data):
            if i < 8:
                stats['byte_ranges'][i].add(byte)
    
    def get_summary(self) -> dict:
        """Generate analysis summary"""
        return {
            'total_messages': len(self.messages),
            'unique_arb_ids': len(self.arb_id_stats),
            'duration': (
                self.messages[-1].timestamp - self.messages[0].timestamp 
                if self.messages else 0
            ),
            'arb_ids': sorted(self.arb_id_stats.keys())
        }
    
    def get_frequencies(self) -> Dict[int, float]:
        """Calculate message frequency for each arbitration ID"""
        frequencies = {}
        for arb_id, stats in self.arb_id_stats.items():
            if stats['intervals']:
                avg_interval = sum(stats['intervals']) / len(stats['intervals'])
                frequencies[arb_id] = 1.0 / avg_interval if avg_interval > 0 else 0
            else:
                frequencies[arb_id] = 0
        return frequencies
    
    def find_counters(self) -> List[Tuple[int, int, str]]:
        """Find bytes that appear to be counters"""
        counters = []
        
        for arb_id, stats in self.arb_id_stats.items():
            samples = stats['data_samples']
            if len(samples) < 10:
                continue
            
            for byte_pos in range(8):
                values = [s[byte_pos] if byte_pos < len(s) else None for s in samples]
                values = [v for v in values if v is not None]
                
                if len(values) < 10:
                    continue
                
                diffs = [values[i+1] - values[i] for i in range(len(values)-1)]
                
                is_4bit_counter = all(
                    (d == 1 or (values[i] == 15 and values[i+1] == 0))
                    for i, d in enumerate(diffs)
                ) and max(values) <= 15
                
                is_8bit_counter = all(
                    (d == 1 or (values[i] == 255 and values[i+1] == 0))
                    for i, d in enumerate(diffs)
                )
                
                if is_4bit_counter:
                    counters.append((arb_id, byte_pos, '4-bit counter'))
                elif is_8bit_counter:
                    counters.append((arb_id, byte_pos, '8-bit counter'))
        
        return counters
    
    def find_sensors(self) -> List[Tuple[int, int, int, dict]]:
        """Find bytes that appear to be sensor values"""
        sensors = []
        
        for arb_id, stats in self.arb_id_stats.items():
            samples = stats['data_samples']
            if len(samples) < 10:
                continue
            
            for byte_pos in range(8):
                values = [s[byte_pos] if byte_pos < len(s) else None for s in samples]
                values = [v for v in values if v is not None]
                
                if len(values) < 10:
                    continue
                
                min_val = min(values)
                max_val = max(values)
                unique = len(set(values))
                
                if unique > 5 and max_val - min_val > 10:
                    sensors.append((arb_id, byte_pos, 1, {
                        'min': min_val,
                        'max': max_val,
                        'unique_values': unique
                    }))
            
            for byte_pos in range(7):
                values = []
                for s in samples:
                    if byte_pos + 1 < len(s):
                        val = (s[byte_pos] << 8) | s[byte_pos + 1]
                        values.append(val)
                
                if len(values) < 10:
                    continue
                
                min_val = min(values)
                max_val = max(values)
                unique = len(set(values))
                
                if unique > 10 and max_val - min_val > 100:
                    sensors.append((arb_id, byte_pos, 2, {
                        'min': min_val,
                        'max': max_val,
                        'unique_values': unique
                    }))
        
        return sensors
    
    def find_constants(self) -> List[Tuple[int, int, int]]:
        """Find bytes that never change"""
        constants = []
        for arb_id, stats in self.arb_id_stats.items():
            for byte_pos, values in enumerate(stats['byte_ranges']):
                if len(values) == 1:
                    constants.append((arb_id, byte_pos, list(values)[0]))
        return constants
    
    def identify_diagnostic_ids(self) -> Dict[int, str]:
        """Identify potential diagnostic/OBD-II arbitration IDs"""
        diagnostic = {}
        for arb_id in self.arb_id_stats.keys():
            if arb_id == 0x7DF:
                diagnostic[arb_id] = "OBD-II Broadcast Request"
            elif 0x7E0 <= arb_id <= 0x7E7:
                diagnostic[arb_id] = f"ECU Request (ECU {arb_id - 0x7E0})"
            elif 0x7E8 <= arb_id <= 0x7EF:
                diagnostic[arb_id] = f"ECU Response (ECU {arb_id - 0x7E8})"
            elif 0x700 <= arb_id <= 0x7FF:
                diagnostic[arb_id] = "Diagnostic Range"
        return diagnostic
    
    def detect_anomalies(self) -> List[dict]:
        """Detect potential security anomalies"""
        anomalies = []
        
        for arb_id, stats in self.arb_id_stats.items():
            if stats['intervals']:
                avg = sum(stats['intervals']) / len(stats['intervals'])
                std_dev = (sum((x - avg) ** 2 for x in stats['intervals']) / len(stats['intervals'])) ** 0.5
                
                if std_dev > avg * 0.5:
                    anomalies.append({
                        'type': 'timing_anomaly',
                        'arb_id': arb_id,
                        'description': f'High timing variance (std_dev={std_dev:.4f}, avg={avg:.4f})'
                    })
            
            samples = stats['data_samples']
            if samples:
                zero_count = sum(1 for s in samples if all(b == 0 for b in s))
                if zero_count > len(samples) * 0.1:
                    anomalies.append({
                        'type': 'data_pattern',
                        'arb_id': arb_id,
                        'description': f'{zero_count}/{len(samples)} messages are all zeros'
                    })
        
        return anomalies
    
    def generate_report(self, output_format: str = 'text') -> str:
        """Generate analysis report"""
        summary = self.get_summary()
        frequencies = self.get_frequencies()
        counters = self.find_counters()
        sensors = self.find_sensors()
        constants = self.find_constants()
        diagnostic = self.identify_diagnostic_ids()
        anomalies = self.detect_anomalies()
        
        if output_format == 'json':
            return json.dumps({
                'summary': summary,
                'frequencies': {f'0x{k:03X}': v for k, v in frequencies.items()},
                'counters': [(f'0x{a:03X}', b, t) for a, b, t in counters],
                'sensors': [(f'0x{a:03X}', b, s, i) for a, b, s, i in sensors],
                'diagnostic_ids': {f'0x{k:03X}': v for k, v in diagnostic.items()},
                'anomalies': anomalies
            }, indent=2)
        
        lines = []
        lines.append("=" * 60)
        lines.append("        CAN BUS TRAFFIC ANALYSIS REPORT")
        lines.append("=" * 60)
        lines.append("")
        lines.append("SUMMARY")
        lines.append("-" * 40)
        lines.append(f"Total Messages:      {summary['total_messages']:,}")
        lines.append(f"Unique Arb IDs:      {summary['unique_arb_ids']}")
        lines.append(f"Duration:            {summary['duration']:.2f} seconds")
        lines.append("")
        
        lines.append("MESSAGE FREQUENCIES (Hz)")
        lines.append("-" * 40)
        for arb_id, freq in sorted(frequencies.items(), key=lambda x: -x[1])[:20]:
            count = self.arb_id_stats[arb_id]['count']
            lines.append(f"  0x{arb_id:03X}: {freq:8.2f} Hz ({count:,} msgs)")
        lines.append("")
        
        if diagnostic:
            lines.append("DIAGNOSTIC IDs DETECTED")
            lines.append("-" * 40)
            for arb_id, desc in sorted(diagnostic.items()):
                lines.append(f"  0x{arb_id:03X}: {desc}")
            lines.append("")
        
        if counters:
            lines.append("POTENTIAL COUNTERS")
            lines.append("-" * 40)
            for arb_id, byte_pos, counter_type in counters:
                lines.append(f"  0x{arb_id:03X} byte {byte_pos}: {counter_type}")
            lines.append("")
        
        if sensors:
            lines.append("POTENTIAL SENSOR VALUES")
            lines.append("-" * 40)
            for arb_id, byte_pos, size, info in sensors[:20]:
                size_str = "8-bit" if size == 1 else "16-bit"
                lines.append(f"  0x{arb_id:03X} byte {byte_pos} ({size_str}): "
                           f"range {info['min']}-{info['max']}, {info['unique_values']} unique")
            lines.append("")
        
        if anomalies:
            lines.append("ANOMALIES DETECTED")
            lines.append("-" * 40)
            for anomaly in anomalies:
                lines.append(f"  0x{anomaly['arb_id']:03X}: {anomaly['description']}")
            lines.append("")
        
        lines.append("=" * 60)
        return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description='CAN Bus Traffic Analyzer')
    parser.add_argument('input', help='Input file (candump or CSV format)')
    parser.add_argument('-f', '--format', choices=['candump', 'csv'], default='candump',
                       help='Input file format')
    parser.add_argument('-o', '--output', choices=['text', 'json'], default='text',
                       help='Output format')
    parser.add_argument('--output-file', help='Write report to file')
    
    args = parser.parse_args()
    
    analyzer = CANAnalyzer()
    
    if args.format == 'candump':
        analyzer.load_candump(args.input)
    else:
        analyzer.load_csv(args.input)
    
    report = analyzer.generate_report(args.output)
    
    if args.output_file:
        with open(args.output_file, 'w') as f:
            f.write(report)
        print(f"Report written to {args.output_file}")
    else:
        print(report)


if __name__ == '__main__':
    main()
