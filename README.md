# TreasureRun（宝探しゲーム / Spigot Plugin）

Minecraft（Spigot）上で遊べる、宝探しミニゲームプラグインです。  
ステージ生成・宝箱スポーン・スコア集計・ランキング・演出（カラフル狼、虹、オーロラ、等）に加え、TreasureShop (謎の取引) による交換要素を入れています。

---

## ゲームの概要

制限時間内に宝箱（Treasure Chest）を探して報酬を獲得し、スコアを競うゲームです。  
難易度によって宝箱の出現数や演出が変化し、ゲーム終了時に結果（順位/報酬）が付与されます。

- 宝箱はゲーム開始時にスポーン（難易度に応じて個数変化）
- ゲーム進行はステージ単位で管理（海上ステージ生成）
- 終了時にランキング演出＋報酬を付与
- スコアはDBに保存し、後から確認可能

---

## 主な機能

-  ステージ生成（海上にネオン床ステージを生成）
-  難易度システム（難易度ブロック等で選択・演出変更）
-  宝箱の複数スポーン（難易度に応じた個数）
-  スコア集計 / ランキング
-  上位報酬＋演出（虹・オーロラ・パーティクル・サウンド等）
-  データ保存（MySQL / SQLite など）

---

## 動作環境

- Minecraft: Spigot 1.20.1
- Java: 17

---

## 導入方法（サーバーに入れる）

1. `TreasureRun-xxx.jar`（ビルド済みJar）を用意します  
2. サーバーの `plugins/` フォルダへJarを入れます  
3. サーバーを起動 / 再起動します  
4. `plugins/TreasureRun/` に設定ファイルが生成されます（初回起動時）

---

## 使い方（ゲーム開始手順）

### 1)（例）ゲーム開始
任意の場所でコマンドを実行してゲームを開始します。

- `【ここに実際の開始コマンドを書く】`
  - 例：`/treasurerun start`
  - 例：`/tr start`

### 2)（例）難易度選択
- `【難易度の選び方を書く（難易度ブロック / GUI / コマンド等）】`

### 3) ゲーム終了
- 制限時間終了、または終了コマンドで終了します
- 結果表示 → ランキング → 報酬付与 → 片付け（ブロック撤去など）

---

## コマンド一覧

| コマンド                 | 説明                                                   |
|--------------------------|--------------------------------------------------------|
| `/treasurerun start`     | TreasureRunのゲームを開始します                        |
| `/treasurerun end`       | 進行中の TreasureRun のゲームを強制終了します        |
| `/treasurerun rank`      | 保存されたスコアからランキングを表示します            |
| `/treasurerun reload`    | 設定ファイル（config.yml）を再読み込みします          |
| `/craftspecialemerald`   | ダイヤモンド3個から特製エメラルドを作成します         |
| `/checktreasureemerald`  | 所持している特製エメラルドの数を確認します            |
| `/gamestart easy`        | ゲームモードeasyを開始します（難易度：初級）          |
| `/gamestart normal`      | ゲームモードnormalを開始します（難易度：中級）        |
| `/gamestart hard`　      | ゲームモードhardを開始します（難易度：上級）    　　|

---

## 設定（config.yml）

- 難易度ごとの宝箱数
- 制限時間
- 演出のON/OFF
- DB接続（MySQL/SQLite）
などを設定できます。

例（※キーはあなたのconfigに合わせて変更）:

```yml
# TreasureRunMultiChestPlugin 用 Config.yml

# 難易度ごとの宝箱数設定
treasureChestCount:
  easy: 3
  normal: 2
  hard: 1

otherChestCount:
  easy: 3
  normal: 5
  hard: 7

totalChestCount:
  easy: 20
  normal: 30
  hard: 40

chestSpawnRadius: 20    # プレイヤー周囲の配置半径

# 宝箱の総数（ゲーム1回で設置する数）
totalChests: 10

allowedDifficulties:
  - easy
  - normal
  - hard

# 宝物のバリエーション
treasureItems:
  - DIAMOND
  - GOLD_INGOT
  - EMERALD
  - IRON_INGOT
  - LAPIS_LAZULI
  - APPLE
  - NETHERITE_INGOT
  - REDSTONE
  - COAL
  - ENCHANTED_GOLDEN_APPLE
  - TNT
  - DIAMOND_BLOCK
  - GOLD_BLOCK
  - EMERALD_BLOCK
  - IRON_BLOCK

# メッセージ設定
messages:
  gameStart:
    - "&aゲーム開始！難易度: %difficulty%"
    - "&bお宝アイテム: %treasureItem%"
    - "&e制限時間: %timeLimit% 秒"
  timeWarning: "&c残り1分です！急いで宝を探してください！"
  gameEnd:
    - "&aゲーム終了！"
    - "&bあなたの得点: %score%"
  chestOpened:
    treasure: "&aお宝を発見しました！ゲームクリア！"
    emptyEasy: "&eこのチェストにはお宝がありません。"
    emptyNormal: "&eお宝なし。時間が減少しました。"
    emptyHard: "&cお宝なし！時間減少＆モンスター出現！"

# スコア設定
scoring:
  maxScore: 1000

# モンスター設定
monsters:
  enabled: true
  spawnCount: 3
  spawnRadius: 10
  monsterTypes:
    - zombie
    - skeleton
    - creeper

# ボスバー設定
bossBar:
  enabled: true
  color: RED
  style: PROGRESS
  title: "制限時間"

# ====================================
# データベース設定（MySQL接続用）
# ====================================
database:
  enabled: true
  host: minecraft_mysql   # ← Docker Compose 内で MySQL コンテナ名
  port: 3306
  database: treasureDB    # MySQL 内の DB 名（TreasureRun が自動作成）
  user: root
  password: your-password-here
  # 注意: MySQL コンテナが起動していないと接続エラーになります。

# ゲーム終了後にプレイヤーの状態を復元するか
restorePlayerStatus: true

# デバッグ用：当たりチェスト座標
winningChestLocation:
  x: 0
  y: 0
  z: 0
  world: world

# =============================
# カスタムクラフトレシピ設定
# =============================
customRecipes:
  - name: "special_emerald"
    type: shaped
    result:
      material: EMERALD
      amount: 1
      displayName: "&6特製エメラルド"
    shape:
      - "DDD"
      - " D "
      - "DDD"
    ingredients:
      D: DIAMOND

  - name: "golden_apple_custom"
    type: shapeless
    result:
      material: APPLE
      amount: 1
      displayName: "&e特製リンゴ"
    ingredients:
      - GOLD_INGOT
      - GOLD_INGOT
      - GOLD_INGOT

  - name: "special_iron_block"
    type: shaped
    result:
      material: IRON_BLOCK
      amount: 1
      displayName: "&7特製鉄ブロック"
    shape:
      - "III"
      - "III"
      - "III"
    ingredients:
      I: IRON_INGOT

---

なスコア競争」にも対応できます。

