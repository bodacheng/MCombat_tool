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

def deleteDirectory(dirPath) {
    println("尝试删除以下：" + dirPath)
    if (fileExists(dirPath)) {
        // echo "File ${path} already exists. Deleting"
        println("删除中：" + dirPath)
        new File(dirPath).delete()
    } else {
        println("null：" + dirPath)
        // echo "File ${path} does not exist."
    }
}

return this
