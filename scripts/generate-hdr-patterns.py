#!/usr/bin/env python3
"""Original, silent diagnostic patterns; PQ code values, not SDR images tagged HDR.

Requires FFmpeg with libx265. Output is 12 s HEVC Main10, limited-range YCbCr,
BT.2020 non-constant luminance + SMPTE ST 2084, max mastering luminance 1000 nit.
Neutral patches occupy only the central 30% of the frame. Not certified calibration.
"""
from pathlib import Path
import subprocess
import math

ROOT = Path(__file__).resolve().parents[1]

def pq_code(nits):
    m1, m2 = 2610 / 16384, 2523 / 32
    c1, c2, c3 = 3424 / 4096, 2413 / 128, 2392 / 128
    linear = (nits / 10000) ** m1
    return round(64 + 876 * ((c1 + c2 * linear) / (1 + c3 * linear)) ** m2)

PATTERNS = {
    'hdr_near_black': [0, .005, .01, .025, .05, .1, .5, 1, 2, 5],
    'hdr_highlights': [10, 100, 203, 400, 600, 800, 1000],
}

def generate():
    for name, levels in PATTERNS.items():
        values = '64'
        for i, value in reversed(list(enumerate(levels))):
            values = f'if(lt(X,{1280 * (i+1) / len(levels)}),{pq_code(value)},{values})'
        expression = f'if(between(Y,252,467),{values},64)'
        graph = f"nullsrc=s=1280x720:r=24:d=12,format=yuv420p10le,geq=lum='{expression}':cb=512:cr=512"
        output = ROOT / 'app/src/main/res/raw' / (name + '.mp4')
        subprocess.run(['ffmpeg','-nostdin','-y','-loglevel','error','-f','lavfi','-i',graph,
            '-an','-c:v','libx265','-preset','fast','-pix_fmt','yuv420p10le',
            '-color_range','tv','-color_primaries','bt2020','-color_trc','smpte2084','-colorspace','bt2020nc',
            '-x265-params','lossless=1:repeat-headers=1:hdr10=1:pools=2:frame-threads=2:log-level=error:master-display=G(8500,39850)B(6550,2300)R(35400,14600)WP(15635,16450)L(10000000,1):max-cll='+str(int(max(levels)))+','+str(math.ceil(sum(levels)/len(levels)*.3)),
            '-tag:v','hvc1','-movflags','+faststart',str(output)],check=True)
        print(name, output.stat().st_size, 'bytes')

if __name__ == '__main__':
    generate()
