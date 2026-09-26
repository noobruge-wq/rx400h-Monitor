"""Build a static Medium font for dynamic Chinese, retaining ALL source Unicode.
Build-only dependency: fonttools 4.64.0. Never changes authored layout/sprites.
No network; input is the existing user-selected Noto Sans SC variable font.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sys


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--tool-path', type=Path, required=True)
    parser.add_argument('--source', type=Path, default=Path('C:/Windows/Fonts/NotoSansSC-VF.ttf'))
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    sys.path.insert(0, str(args.tool_path.resolve()))
    import fontTools
    from fontTools.ttLib import TTFont
    from fontTools.varLib.instancer import instantiateVariableFont
    from fontTools import subset

    font = TTFont(args.source, recalcTimestamp=False)
    original_codepoints = set(font.getBestCmap())
    copyright_notice = font['name'].getDebugName(0)
    license_notice = font['name'].getDebugName(13)
    font = instantiateVariableFont(font, {'wght': 500}, inplace=True, optimize=True)
    options = subset.Options()
    options.name_IDs = ['*']
    options.name_legacy = True
    options.name_languages = ['*']
    options.hinting = True
    # This UI uses SC horizontal text only. Keep combining/kerning support but
    # omit unused regional/vertical alternate glyph substitutions. Cmap glyphs
    # (including all source Chinese code points) remain byte-for-byte addressable.
    options.layout_features = ['ccmp', 'kern', 'mark', 'mkmk']
    subsetter = subset.Subsetter(options=options)
    subsetter.populate(unicodes=original_codepoints)
    subsetter.subset(font)
    # Name the modified static derivative; provenance/copyright remain intact.
    renamed = {1: 'RX400h UI Sans SC', 2: 'Medium', 3: 'RX400hUISansSC-Medium-1',
               4: 'RX400h UI Sans SC Medium', 6: 'RX400hUISansSC-Medium',
               16: 'RX400h UI Sans SC', 17: 'Medium'}
    for record in list(font['name'].names):
        if record.nameID in renamed:
            font['name'].setName(renamed[record.nameID], record.nameID,
                                 record.platformID, record.platEncID, record.langID)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    font.save(args.output)
    actual = TTFont(args.output, recalcTimestamp=False)
    assert set(actual.getBestCmap()) == original_codepoints, 'Unicode coverage changed'
    assert 'fvar' not in actual, 'Variable axes unexpectedly retained'
    metadata = {'source': args.source.name, 'source_sha256': hashlib.sha256(args.source.read_bytes()).hexdigest(),
                'source_bytes': args.source.stat().st_size, 'output_bytes': args.output.stat().st_size,
                'output_sha256': hashlib.sha256(args.output.read_bytes()).hexdigest(),
                'weight': 500, 'unicode_count': len(original_codepoints), 'coverage_dropped': 0,
                'fonttools_version': fontTools.__version__, 'copyright': copyright_notice,
                'license_notice': license_notice,
                'purpose': 'Physical-pixel dynamic Chinese/status/settings; fixed authored sprites unchanged'}
    args.output.with_suffix('.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(metadata, ensure_ascii=True))


if __name__ == '__main__':
    main()
