# Hybrid i18n verification: Fabric runtime sync and non-mod ResourcePack fallback

## Summary

Fabric Mod導入済みクライアントでは、`/lang` で選択された言語コードを Plugin Message 経由で Fabric 側へ同期し、`LanguageManager` と `reloadResources()` によって Minecraft 標準UIの言語反映を行う。

一方で、Fabric Mod未導入クライアントでは、サーバー側から Minecraft クライアント本体の `options.language` を直接変更することはできない。

そのため、Modなし環境では **ResourcePack alias fallback** を用意し、ResourcePackが適用される範囲で Minecraft 標準 translation key 表示を可能な限り選択言語へ寄せる設計にした。

この検証では、20言語分の fallback ResourcePack、8039キー整合性、SHA1整合性、Java側の fallback 分岐をターミナルで静的検証した。

---

## Design

```text
Fabric Modあり:
  /lang -> Plugin Message -> client.options.language -> LanguageManager -> reloadResources

Fabric Modなし:
  /lang -> language-specific ResourcePack fallback
        -> locale alias JSON override
        -> Bukkit / ProtocolLib message replacement
```

Modなし環境向けには、言語別の fallback ResourcePack を生成する。

```text
resourcepacks/generated/treasurerun-i18n-pack-ja.zip
resourcepacks/generated/treasurerun-i18n-pack-en.zip
resourcepacks/generated/treasurerun-i18n-pack-de.zip
...
```

各 fallback pack の中には、複数の locale 名に対して同じ選択言語の翻訳JSONを alias 配置する。

これにより、クライアント本体の `options.language` をサーバーから変更できない場合でも、現在のクライアント言語設定で参照される locale JSON を ResourcePack 側から上書きし、表示できる範囲を補完する。

---

## Verification scope

この検証で確認したこと:

```text
- 20言語分の fallback ZIP が存在する
- 各 ZIP は複数 locale 名へ alias 配置されている
- 各選択言語 JSON は Minecraft 1.20.1 標準翻訳キー 8039 keys に正規化されている
- config.yml の SHA1 と ZIP 実体が一致している
- Fabric Modなしの場合に ResourcePackFallbackService へ流す実装がある
- Gradle build が成功する
```

---

## Terminal evidence

### 1. Temporary scripts cleanup

```text
=== 1) Confirm no temporary scripts remain ===
OK: no untracked tmp scripts
```

### 2. 20 language JSON key count verification

Fabric Mod側と ResourcePack側の両方で、20言語すべての lang JSON が 8039 keys に揃っていることを確認した。

```text
OK: all checked lang JSONs have 8039 keys
```

検証対象:

```text
ja        8039 keys
en        8039 keys
de        8039 keys
es        8039 keys
fr        8039 keys
ko        8039 keys
ru        8039 keys
zh_tw     8039 keys
fi        8039 keys
it        8039 keys
nl        8039 keys
pt        8039 keys
hi        8039 keys
sv        8039 keys
is        8039 keys
la        8039 keys
sa        8039 keys
lzh       8039 keys
ojp       8039 keys
asl_gloss 8039 keys
```

### 3. Fallback ZIP and SHA1 verification

20言語分の fallback ZIP について、`config.yml` に記載された SHA1 と実際のZIPファイルのSHA1が一致していることを確認した。

```text
OK: all fallback ZIP SHA1 values match config.yml
```

### 4. ResourcePack alias fallback verification

Modなし環境向けの ResourcePack alias fallback が、20言語すべてで静的検証を通過した。

```text
PASS: Modなし ResourcePack alias fallback is statically verified.
```

判定:

```text
- 20言語分のfallback ZIPは存在します。
- 各ZIPは複数locale名へalias配置されています。
- 各選択言語JSONは8039 keysです。
- config.ymlのSHA1とZIP実体が一致しています。
- Fabric Modなしの場合にResourcePackFallbackServiceへ流す実装があります。
```

### 5. Java routing verification

Fabric Modあり / なし の分岐と、Modなしの場合の fallback 経路が存在することを確認した。

```text
✅ LangCommand calls LanguageSyncService
✅ LangCommand checks FabricModDetector
✅ LangCommand sends fallback when Fabric is absent
✅ ResourcePackFallbackService uses setResourcePack
✅ ResourcePackFallbackService reads resourcePackFallback.packs
✅ FabricModDetector listens for treasurerun:hello
✅ Main plugin registers FabricModDetector
✅ Main plugin registers ResourcePackFallbackService
✅ Join listener resends fallback on join
```

### 6. Build verification

```text
BUILD SUCCESSFUL
```

### 7. Commit and push evidence

検証後、以下のコミットとして GitHub の main へ反映した。

```text
4759506 fix: normalize non-mod i18n fallback packs to 8039 keys
```

push 後の状態:

```text
Your branch is up to date with 'origin/main'.

nothing to commit, working tree clean
```

---

## Result

この検証により、Fabric Modなし環境向けに、20言語分の ResourcePack alias fallback を静的検証できた。

具体的には、各 fallback ZIP に複数 locale alias を配置し、各選択言語JSONを Minecraft 1.20.1 標準翻訳キー 8039 keys に正規化した。さらに、`config.yml` の SHA1 と ZIP 実体の整合性、Java側の fallback 分岐、Gradle build まで確認した。

これにより、Fabric Modなし環境でも、ResourcePackが適用される範囲では Minecraft 標準 translation key 表示を可能な限り選択言語へ寄せる fallback 基盤を整備できた。

---

## Limitation

この検証は静的検証である。

Fabric Modなし環境では、Minecraftクライアント本体の `options.language` をサーバーから直接変更することはできない。そのため、Fabric Modありの場合と同じ完全な自動言語切替ではない。

実際にMinecraftクライアントで表示が切り替わるかは、クライアント側で ResourcePack を受諾し、画面上で確認する必要がある。

ただし、ResourcePack fallbackとして必要なファイル構成、8039キー整合性、SHA1整合性、Java側のfallback分岐はターミナル上で確認済み。
