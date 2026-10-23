# 1.1.26 Towny protection / PR #2統合

`mikan6139/AnchoredFreecam` の `towny-protection`（`ee3b81c146b1c246706ccb11d11dd61ecdb7c5a9`）を、1.1.25の敵対・再捕捉修正を含むmainへ統合。

競合は `build.gradle.kts` のバージョン番号のみ。PR側の `1.1.26` を採用した。既存の `BodyMobBridge`、敵対・再捕捉処理、水中・移動・インベントリ・ペット連携の修正を保持する。READMEのPR差分は機能説明と権限説明であり、更新履歴は追加しない。

## 追加される機能

- Towny導入時に連携し、保護された他TownへのFreecamカメラの侵入・内部での開始を制限する。自分が所属するTownと荒野は対象外。
- `/freecam town` の設定画面、`/freecam town on|off|status`。TownのMayorまたはAssistantが設定でき、`anchoredfreecam.towny.manage` も必要。
- `anchoredfreecam.towny.bypass` による制限の回避。`anchoredfreecam.admin` に含まれる。
- Townごとの設定は `towns.yml` に保存。未設定のTownは `towny-protection.default-protected: true` に従う。

## 検証

Java 25 / Paper API `26.2.build.129-stable` で統合後のビルド成功、自動テスト218件成功。PRのTowny判定・保存テストに加え、Town境界でセッションを終了せず移動を拒否すること、予約済み補正先の保護再判定、本体位置が保護対象になった場合の終了、外部TPによる終了との併用の4件を追加した。

既存の202件の回帰テストも含む。TownyのテストはPRに含まれるAPIスタブおよびモックを使用しており、実際のTowny JAR、Paper/Purpur実サーバー、ゲーム内設定画面での動作は未検証。

PR元のGitHub Actionsは確認時点で `action_required`。この状態をビルド成功とは扱わず、統合内容はローカルで上記ビルド・テストを実施した。
