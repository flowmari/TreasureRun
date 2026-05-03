# 20-language native-quality audit

Generated: 2026-05-04T04:47:29

## Summary

| lang | total | OK | WARN | NG |
|---|---:|---:|---:|---:|
| ja | 376 | 96 | 114 | 166 |
| en | 376 | 219 | 0 | 157 |
| de | 376 | 156 | 54 | 166 |
| it | 376 | 140 | 70 | 166 |
| sv | 376 | 153 | 57 | 166 |
| es | 376 | 140 | 70 | 166 |
| fi | 376 | 142 | 68 | 166 |
| fr | 376 | 142 | 68 | 166 |
| hi | 376 | 181 | 23 | 172 |
| is | 376 | 142 | 68 | 166 |
| ko | 376 | 140 | 70 | 166 |
| la | 376 | 139 | 71 | 166 |
| lzh | 376 | 185 | 19 | 172 |
| nl | 376 | 140 | 71 | 165 |
| ojp | 376 | 119 | 90 | 167 |
| pt | 376 | 182 | 22 | 172 |
| ru | 376 | 142 | 68 | 166 |
| sa | 376 | 140 | 70 | 166 |
| zh_tw | 376 | 177 | 21 | 178 |
| asl_gloss | 376 | 106 | 104 | 166 |

## Meaning

- `NG_MISSING_KEY`: 翻訳キーが存在しません。
- `NG_TRANSLATION_MISSING_TEXT`: 画面に出してはいけない Translation missing 文が残っています。
- `NG_PLACEHOLDER_MISMATCH`: `{arg0}` などの変数が英語版と一致していません。
- `WARN_EQUALS_ENGLISH`: 英語コピーの可能性があります。自然な20言語化としては要修正です。
- `WARN_ROMAN_TEXT_IN_JAPANESE_LANG`: 日本語/古文に英語っぽい文字が残っています。
- `WARN_CJK_TEXT_IN_NON_CJK_LANG`: 非CJK言語に日本語/中国語文字が混ざっています。
