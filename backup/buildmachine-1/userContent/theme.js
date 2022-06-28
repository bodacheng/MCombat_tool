var titleStr = "[ポケピア]"

// タイトル変更(タブのとこに表示されるタイトル）
window.addEventListener("load", function() {
  var title = document.querySelector('title')
  title.innerHTML = title.innerHTML.replace(/\[Jenkins\]/, titleStr)
}, false);



// コンテンツの中のタイトルを変更
document.addEventListener("DOMContentLoaded", function(){
  var img = document.getElementsBySelector('[id="jenkins-home-link"]')[0];
  var alink = img.parentNode;
  alink.appendChild(document.createTextNode(titleStr));
  alink.setAttribute("class", "header-text");
}, false);
