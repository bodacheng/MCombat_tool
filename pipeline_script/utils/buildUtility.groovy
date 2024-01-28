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
    File directory = new File(dirPath)

    if (!directory.exists()) {
        println("Directory does not exist: $dirPath")
        return
    }

    try {
        directory.deleteDir()
        println("Deleted directory: $dirPath")
    } catch (Exception e) {
        println("Error deleting directory: $dirPath")
        e.printStackTrace()
    }
}

return this
