/**
 * ビルド種別からアセット種別を割り当て
 * （基本的には一緒）
 * @param  BuidKind               [description]
 * @return          [description]
 */
def getAssetKind(buildKind) {
    if ("QA".equals(buildKind)) {
        return "Dev"
    }
    else if ("Beta".equals(buildKind)) {
        return "Beta"
    }
    else if ("Release".equals(buildKind)) {
        return "Release"
    }

    return buildKind
}

return this
