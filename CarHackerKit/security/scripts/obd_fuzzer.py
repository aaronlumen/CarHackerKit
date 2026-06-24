#!/usr/bin/env python3
"""
OBD-II Protocol Fuzzer
From "The Car Hacker's Handbook" techniques

Generates test cases for OBD-II protocol fuzzing:
- PID brute forcing
- Mode enumeration
- Boundary testing
- Malformed packet generation

WARNING: For isolated test bench use only!
"""

import random
import struct
import itertools
from typing import Generator, List, Tuple
from dataclasses import dataclass
from enum import Enum


class FuzzStrategy(Enum):
    SEQUENTIAL = "sequential"
    RANDOM = "random"
    BOUNDARY = "boundary"
    BITFLIP = "bitflip"
    MUTATION = "mutation"


@dataclass
class FuzzCase:
    """A single fuzz test case"""
    mode: int
    pid: int
    data: bytes
    description: str
    category: str


class OBDFuzzer:
    """OBD-II Protocol Fuzzer"""
    
    # Standard OBD-II modes
    MODES = {
        0x01: "Show current data",
        0x02: "Show freeze frame data",
        0x03: "Show stored DTCs",
        0x04: "Clear DTCs",
        0x05: "Test results (O2 sensors)",
        0x06: "Test results (other)",
        0x07: "Show pending DTCs",
        0x08: "Control operation",
        0x09: "Request vehicle info",
        0x0A: "Permanent DTCs",
    }
    
    # Boundary values for testing
    BOUNDARY_VALUES = [
        0x00, 0x01, 0x7F, 0x80, 0xFE, 0xFF,
    ]
    
    def __init__(self, seed: int = None):
        if seed is not None:
            random.seed(seed)
    
    def generate_pid_scan(self, mode: int = 0x01) -> Generator[FuzzCase, None, None]:
        """Generate sequential PID scan for a mode"""
        for pid in range(0x00, 0x100):
            yield FuzzCase(
                mode=mode,
                pid=pid,
                data=bytes([mode, pid]),
                description=f"Mode {mode:02X} PID {pid:02X}",
                category="pid_scan"
            )
    
    def generate_mode_scan(self) -> Generator[FuzzCase, None, None]:
        """Scan all possible modes with PID 0x00"""
        for mode in range(0x00, 0x100):
            yield FuzzCase(
                mode=mode,
                pid=0x00,
                data=bytes([mode, 0x00]),
                description=f"Mode {mode:02X} probe",
                category="mode_scan"
            )
    
    def generate_manufacturer_scan(self) -> Generator[FuzzCase, None, None]:
        """Scan manufacturer-specific mode range (0x21-0x3E)"""
        for mode in range(0x21, 0x3F):
            for pid in range(0x00, 0x100):
                yield FuzzCase(
                    mode=mode,
                    pid=pid,
                    data=bytes([mode, pid]),
                    description=f"Manufacturer mode {mode:02X} PID {pid:02X}",
                    category="manufacturer_scan"
                )
    
    def generate_boundary_cases(self) -> Generator[FuzzCase, None, None]:
        """Generate boundary value test cases"""
        for mode in self.MODES.keys():
            for pid in self.BOUNDARY_VALUES:
                yield FuzzCase(
                    mode=mode,
                    pid=pid,
                    data=bytes([mode, pid]),
                    description=f"Boundary: mode {mode:02X}, pid {pid:02X}",
                    category="boundary"
                )
        
        # Extended data boundary cases
        for mode in [0x01, 0x22, 0x2E]:
            for val in self.BOUNDARY_VALUES:
                data = bytes([mode, 0x00, val, val, val, val])
                yield FuzzCase(
                    mode=mode,
                    pid=0x00,
                    data=data,
                    description=f"Extended boundary: mode {mode:02X}, val {val:02X}",
                    category="boundary_extended"
                )
    
    def generate_malformed_packets(self) -> Generator[FuzzCase, None, None]:
        """Generate malformed OBD-II packets"""
        
        # Empty packet
        yield FuzzCase(mode=0, pid=0, data=b'', description="Empty packet", category="malformed")
        
        # Single byte
        for b in [0x00, 0x01, 0x7F, 0xFF]:
            yield FuzzCase(mode=b, pid=0, data=bytes([b]), description=f"Single byte {b:02X}", category="malformed")
        
        # Oversized packets
        for size in [9, 16, 32, 64, 128, 255]:
            data = bytes([0x01, 0x00] + [0x55] * (size - 2))
            yield FuzzCase(mode=0x01, pid=0x00, data=data, description=f"Oversized {size} bytes", category="malformed")
        
        # All zeros
        for size in range(1, 9):
            yield FuzzCase(mode=0, pid=0, data=bytes(size), description=f"All zeros {size}B", category="malformed")
        
        # All 0xFF
        for size in range(1, 9):
            yield FuzzCase(mode=0xFF, pid=0xFF, data=bytes([0xFF] * size), description=f"All 0xFF {size}B", category="malformed")
    
    def generate_uds_probes(self) -> Generator[FuzzCase, None, None]:
        """Generate UDS (Unified Diagnostic Services) probes"""
        
        # UDS Service IDs
        uds_services = {
            0x10: "DiagnosticSessionControl",
            0x11: "ECUReset",
            0x14: "ClearDTC",
            0x19: "ReadDTC",
            0x22: "ReadDataByIdentifier",
            0x23: "ReadMemoryByAddress",
            0x27: "SecurityAccess",
            0x28: "CommunicationControl",
            0x2E: "WriteDataByIdentifier",
            0x2F: "InputOutputControl",
            0x31: "RoutineControl",
            0x34: "RequestDownload",
            0x35: "RequestUpload",
            0x36: "TransferData",
            0x37: "RequestTransferExit",
            0x3E: "TesterPresent",
        }
        
        for sid, name in uds_services.items():
            # Basic probe
            yield FuzzCase(mode=sid, pid=0x00, data=bytes([sid, 0x00]), 
                          description=f"UDS {name} basic", category="uds")
            
            # With subfunctions
            for sub in [0x00, 0x01, 0x02, 0x03, 0xFF]:
                yield FuzzCase(mode=sid, pid=sub, data=bytes([sid, sub]),
                              description=f"UDS {name} sub {sub:02X}", category="uds")
        
        # Security access seed requests
        for level in [0x01, 0x03, 0x05, 0x07, 0x11, 0x21]:
            yield FuzzCase(mode=0x27, pid=level, data=bytes([0x27, level]),
                          description=f"SecurityAccess level {level:02X}", category="uds_security")
        
        # Security access key attempts (common weak keys)
        weak_keys = [
            bytes([0x27, 0x02, 0x00, 0x00, 0x00, 0x00]),
            bytes([0x27, 0x02, 0xFF, 0xFF, 0xFF, 0xFF]),
            bytes([0x27, 0x02, 0x12, 0x34, 0x56, 0x78]),
            bytes([0x27, 0x02, 0xDE, 0xAD, 0xBE, 0xEF]),
        ]
        for key in weak_keys:
            yield FuzzCase(mode=0x27, pid=0x02, data=key,
                          description=f"WeakKey {key.hex()}", category="uds_security")
    
    def generate_random_cases(self, count: int = 1000) -> Generator[FuzzCase, None, None]:
        """Generate random fuzz cases"""
        for i in range(count):
            length = random.randint(1, 8)
            data = bytes(random.randint(0, 255) for _ in range(length))
            yield FuzzCase(
                mode=data[0] if data else 0,
                pid=data[1] if len(data) > 1 else 0,
                data=data,
                description=f"Random case {i}",
                category="random"
            )
    
    def generate_bitflip_cases(self, base_data: bytes) -> Generator[FuzzCase, None, None]:
        """Generate bit-flip mutations of base data"""
        for byte_idx in range(len(base_data)):
            for bit_idx in range(8):
                mutated = bytearray(base_data)
                mutated[byte_idx] ^= (1 << bit_idx)
                yield FuzzCase(
                    mode=mutated[0] if mutated else 0,
                    pid=mutated[1] if len(mutated) > 1 else 0,
                    data=bytes(mutated),
                    description=f"Bitflip byte{byte_idx} bit{bit_idx}",
                    category="bitflip"
                )
    
    def generate_campaign(self, strategy: FuzzStrategy = FuzzStrategy.SEQUENTIAL,
                         max_cases: int = None) -> Generator[FuzzCase, None, None]:
        """Generate a full fuzzing campaign"""
        
        generators = []
        
        if strategy == FuzzStrategy.SEQUENTIAL:
            generators = [
                self.generate_mode_scan(),
                self.generate_pid_scan(0x01),
                self.generate_pid_scan(0x09),
                self.generate_manufacturer_scan(),
            ]
        elif strategy == FuzzStrategy.BOUNDARY:
            generators = [
                self.generate_boundary_cases(),
                self.generate_malformed_packets(),
            ]
        elif strategy == FuzzStrategy.RANDOM:
            generators = [
                self.generate_random_cases(max_cases or 10000),
            ]
        else:
            generators = [
                self.generate_mode_scan(),
                self.generate_boundary_cases(),
                self.generate_malformed_packets(),
                self.generate_uds_probes(),
                self.generate_random_cases(1000),
            ]
        
        count = 0
        for gen in generators:
            for case in gen:
                yield case
                count += 1
                if max_cases and count >= max_cases:
                    return
    
    def export_cases(self, cases: List[FuzzCase], filename: str, fmt: str = 'csv'):
        """Export fuzz cases to file"""
        if fmt == 'csv':
            with open(filename, 'w') as f:
                f.write("mode,pid,data_hex,description,category\n")
                for case in cases:
                    f.write(f"0x{case.mode:02X},0x{case.pid:02X},"
                           f"{case.data.hex()},{case.description},{case.category}\n")
        elif fmt == 'bin':
            with open(filename, 'wb') as f:
                for case in cases:
                    f.write(struct.pack('B', len(case.data)))
                    f.write(case.data)


def main():
    import argparse
    
    parser = argparse.ArgumentParser(description='OBD-II Protocol Fuzzer')
    parser.add_argument('-s', '--strategy', choices=['sequential', 'boundary', 'random', 'all'],
                       default='all', help='Fuzzing strategy')
    parser.add_argument('-n', '--max-cases', type=int, help='Maximum number of cases')
    parser.add_argument('-o', '--output', help='Output file')
    parser.add_argument('--seed', type=int, help='Random seed for reproducibility')
    
    args = parser.parse_args()
    
    fuzzer = OBDFuzzer(seed=args.seed)
    
    strategy_map = {
        'sequential': FuzzStrategy.SEQUENTIAL,
        'boundary': FuzzStrategy.BOUNDARY,
        'random': FuzzStrategy.RANDOM,
        'all': FuzzStrategy.MUTATION,
    }
    
    cases = list(fuzzer.generate_campaign(
        strategy=strategy_map[args.strategy],
        max_cases=args.max_cases
    ))
    
    print(f"Generated {len(cases)} fuzz cases")
    
    if args.output:
        fuzzer.export_cases(cases, args.output)
        print(f"Exported to {args.output}")
    else:
        # Print first 20 cases
        for case in cases[:20]:
            print(f"[{case.category}] {case.description}: {case.data.hex()}")
        if len(cases) > 20:
            print(f"... and {len(cases) - 20} more")


if __name__ == '__main__':
    main()
