# 1.1.23 MyPet操作禁止の検証メモ

## 漏れていた経路

MyPet 3.14.3の配布JARで `PlayerListener#on(PlayerInteractEntityEvent)` を確認した。Paper 26.2の内部版 `v26_2_R1` はこの処理の対象。`MONITOR` 優先度で、キャンセル済みの右クリック対象が所有するMyPetである場合に `setCancelled(false)` を呼ぶ。

1.1.22のFreecamはエンティティ右クリックを `LOWEST` でキャンセルするだけだったため、このMyPetの処理後にはキャンセルが残らない。MyPetのゾンビ型の `handlePlayerInteraction` には装備の取付・取外し処理がある。このため「操作すべて無効化済み」とは言えない状態だった。

検査したMyPet JARのSHA-256：`170aafa9b7d73c56b90ccb312026af46342ec7981ef848ee165c68e6c3682269`。

## 修正

- 両手の `PlayerInteractEntityEvent` / `PlayerInteractAtEntityEvent` に、後段の最終キャンセルを追加。MyPetがMONITORで解除するため、この互換処理もMONITORで実行する。
- 既存の `softdepend: MyPet` によりMyPetの有効化・リスナー登録後に登録する。配布JAR内でMyPetがPlayerListenerを有効化時に登録することも確認した。
- 最終段階ではFreecam中の操作者、または他人の幽体・本体への操作を再禁止する。本体右クリックによる終了は既存の入口処理を維持する。
- インベントリのクリック・ドラッグは既存の後段キャンセルに加えてLOWESTでも止める。Freecam中の収納画面の新規オープンもLOWEST/HIGHESTでキャンセルする。
- 通常時のペット右クリックと収納操作は維持する。移動・コマンドを一律禁止する修正ではない。

## 検証

Java 25 / Paper API 26.2.build.129-stableでビルド成功、154件の自動テスト成功。メイン・オフハンド、位置指定クリック、他処理によるキャンセル解除、通常操作、幽体・本体の保護、収納画面とクリック・ドラッグを検証した。

別のモック検証では、配布JARの実際のMyPet PlayerListenerを呼び出した。FreecamのLOWEST処理で禁止 → MyPetのMONITOR処理で解除されることを再現 → 新しい最終処理で再禁止、を両手で確認した。Freecam解除後の通常操作が禁止されないことも確認した。

実サーバー・ゲームクライアントでの装備消費／ペット装備状態の確認は未実施。他プラグインを含めたあらゆる独自操作経路の完全な無効化は、このテスト結果だけでは保証しない。実機では武器・防具を渡す、ハサミで装備を外す、餌、両手、通常時の同じ操作、収納コマンド経由のGUIを確認する必要がある。READMEは変更していない。
