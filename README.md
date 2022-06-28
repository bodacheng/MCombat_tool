# 概要
ビルドマシンに付随するバックアップファイル置き場です。  
他の用途もあるかもしれないので、適宜ドキュメントを修正してください。

ポータルのページに別途jenkinsについての記載もあるので、p3のポータルページも参照してください。

- [20. Jenkinsビルド環境作成手順](https://portal.cocone.jp/confluence/pages/viewpage.action?pageId=112324426)

# バックアップファイルについて
jenkinsのバックアップファイルをthinbackup pluginで保存します。（定期的）  
保存したスクリプトをjenkinsマシンから、定期ジョブで自動的にgitにpushします。  
そのため、ビルドマシンが変わったり、バックアップファイルの上げ先が変更になる場合、開発者は運用に注意してください。  

# pipeline scriptについて
pipeline script from scm を使ってjenkinsを運用しています。
jenkinsで使っているscriptは、pipeline_script/... のファイル群です。

# 各種jenkinsマシン
jenkinsマシンのアドレス等について記載します。

- buildmachine-1 (http://pokepia-buildmachine-1.cocone.jp:8080/)
- slave1 (pokepia-buildmachine-3.cocone.jp)
# MCombat_tool
