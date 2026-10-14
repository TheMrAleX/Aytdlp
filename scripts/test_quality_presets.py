#!/usr/bin/env python3
"""Verify quality fallback against the exact bundled yt-dlp, without networking."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'app/src/main/assets/yt-dlp'))
import yt_dlp


class QualityPresetsTest(unittest.TestCase):
    def select(self, heights, target, portrait=False):
        formats = [dict(format_id=str(h), url=f'https://example.invalid/{h}.mp4',
                        height=h * 16 // 9 if portrait else h,
                        width=h if portrait else h * 16 // 9,
                        ext='mp4', vcodec='avc1', acodec='mp4a') for h in heights]
        with yt_dlp.YoutubeDL(dict(quiet=True, skip_download=True, format='bv*+ba/b',
                                  format_sort=[f'res:{target}'] if target else [])) as ydl:
            result = ydl.process_ie_result(dict(id='fixture', title='Fixture', formats=formats), download=False)
            return int(result['format_id'])

    def test_exact_quality(self):
        self.assertEqual(480, self.select([144, 360, 480, 720], 480))

    def test_video_below_target(self):
        self.assertEqual(360, self.select([144, 240, 360], 480))

    def test_only_higher_formats(self):
        self.assertEqual(720, self.select([720, 1080], 480))

    def test_best_quality(self):
        self.assertEqual(2160, self.select([360, 720, 1080, 2160], 0))

    def test_portrait_resolution(self):
        self.assertEqual(480, self.select([360, 480, 720], 480, portrait=True))


if __name__ == '__main__':
    unittest.main()
