# 1.1.22 ポータル検証メモ

## 報告とコード上の問題

報告：「本体がネザゲに入る。/freecamすると溜めなしで移動できる。もう一度では移動しない」。開始・終了のどちらの操作でも、本体が先にポータル移動しないようにする。

1.1.21の `onPortalEnter` はFreecam中のPlayerだけをキャンセルしており、本体Mannequinを対象にしていなかった。`PlayerPortalEvent` も本体には適用されない。本体の位置は `syncAnchorToBody` と `stop` の帰還先に使われるため、本体が別ワールドへ移動すると本人の帰還先も変わる。

ローカルのPaper 26.2.build.129-stable実装を確認した。`NetherPortalBlock#getPortalTransitionTime` はPlayerではゲームルールの待ち時間を参照し、Player以外では0を返す。`entityInside` は `EntityPortalEnterEvent` のキャンセル後には `setAsInsidePortal` を呼ばない。これにより、本体の入口で止める修正は待ち時間なしの本体転送を防ぐ経路となる。利用者の実サーバー上で同じ順序が起きたことの確認は未実施。

## 変更

- Freecam中のPlayerと、登録済みの本体UUIDを共通の対象判定にする。
- `EntityPortalEnterEvent` で本体のポータル進入処理をキャンセル。
- 既存の進行中処理に対しても `EntityPortalReadyEvent` と `EntityPortalEvent` で転送をキャンセル。
- 既存の幽体向け `PlayerPortalEvent` / ポータル由来 `PlayerTeleportEvent` の拒否は維持。
- 通常プレイヤー・無関係のMannequinのポータル利用は変更しない。固定のクールダウン追加やゲームルール変更は行わない。

## 検証

Java 25 / Paper API 26.2.build.129-stableでビルド成功、144件のモックテスト成功。追加テストは全PortalTypeでの本体の入口・準備完了・転送の拒否、開始前から進行していた幽体の準備完了処理の拒否、初回と次セッションの帰還先ワールド維持、無関係なPlayer/Mannequinの通常利用。

実機での待ち時間・描画・イベント順は未検証。ポータル内で開始するケース、Freecam中に本体がポータルへ押されるケース、手動終了、繰り返し切替、通常状態でのポータル利用を確認する必要がある。READMEは変更していない。
