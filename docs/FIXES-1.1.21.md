# 1.1.21 検証メモ

## 統合版のホットバー

1.1.20では `ClientboundSetPlayerInventoryPacket` に選択スロットと装備のインベントリ番号36～40を送っていました。確認したGeyserの `JavaSetPlayerInventoryTranslator` は0～8だけをホットバー用に変換し、36～40はそのままコンテナのスロットとして更新するため、装備用の空通知がホットバー1～5に届きます。

1.1.21は `ClientboundContainerSetSlotPacket` のウィンドウ0に統一します。選択中は36～44、頭から足は5～8、オフハンドは45です。選択が変わると前のスロットの実アイテムを本人に再送します。終了時に選択の記録を破棄し、既存の全インベントリ同期で表示を戻します。実インベントリと本体装備を書き換える処理は追加していません。

参照：
- https://github.com/GeyserMC/Geyser/blob/master/core/src/main/java/org/geysermc/geyser/translator/protocol/java/inventory/JavaSetPlayerInventoryTranslator.java
- Paper 26.2.build.129-stable の `ClientboundContainerSetSlotPacket`、`InventoryMenu`、`AbstractContainerMenu#getStateId`、`CraftItemStack#asNMSCopy` のシグネチャ。

## PvP保護とFreecam終了

1.1.20は本体への攻撃で先にセッションを終了し、その後に本人へネイティブのダメージを転送していました。そのため、本人への攻撃が保護プラグインにキャンセルされても、Freecamの終了は取り消せませんでした。

1.1.21はセッションを残した状態で、同じ `DamageSource` を本人の通常のダメージ処理へ渡します。その本人イベントをLOWESTで記録し、MONITORでキャンセルと最終ダメージを確認します。キャンセル、最終ダメージ0、ネイティブ側でイベントを発生させず拒否した場合は継続します。許可された攻撃だけ、ダメージ適用・死亡処理より先に本体へ帰還します。`exit-on-body-damage: false` では従来どおり帰還しません。

指定されたPvPManager 4.0.22の配布JARを確認しました。`EntityListener#onPlayerDamage` は本人を対象とする `EntityDamageByEntityEvent` で `PlayerManager#checkProtection` を呼び、保護対象ならキャンセルします。マネキンだけを攻撃したイベントは同じPvP判定になりません。今回の修正は特定APIへの依存追加ではなく、転送先の本人イベントのキャンセル結果を尊重します。PvPManagerと実サーバーを起動した結合試験は未実施です。

参照：https://modrinth.com/plugin/pvpmanager/version/4.0.22

## 本体のプレイヤーヘッド：未修正

利用者から「残した本体に装着したプレイヤーヘッド」と確認済みです。本体へのヘルメット複製処理は維持しています。確認したGeyserの `JavaSetEquipmentTranslator` は、スキン付きプレイヤーヘッドの `FakeHeadProvider#setHead` を `PlayerEntity` に限定しています。`MannequinEntity` は `AvatarEntity` を継承しており、この条件に入りません。今回のスロット修正でこの表示制限まで解消したとは確認していません。利用サーバーのGeyser版での描画確認も必要です。

参照：
- https://github.com/GeyserMC/Geyser/blob/master/core/src/main/java/org/geysermc/geyser/translator/protocol/java/entity/JavaSetEquipmentTranslator.java
- https://github.com/GeyserMC/Geyser/blob/master/core/src/main/java/org/geysermc/geyser/entity/type/player/MannequinEntity.java

## 検証範囲

Java 25 / Paper API 26.2.build.129-stableでビルド。モックによる回帰テストでは、全9スロットの送信先、選択変更時の実アイテム再送、終了後の記録破棄、パケット失敗時の同期、保護キャンセル時の本体維持、イベントなしの拒否、許可された致死ダメージの帰還順を検証しました。既存の水中・境界・操作禁止・本体物理・ペットなどのテストも実行しています。

Paper/Purpurの実稼働サーバー、Geyserクライアント、PvPManagerを同時起動した再現試験は未実施です。実機ではPvP無効の攻撃者／被害者、近接／飛び道具、PvP有効時の通常帰還、全ホットバーの切替、終了後の装備復元を確認してください。READMEに更新情報は追加していません。
