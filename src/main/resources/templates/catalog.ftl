<!DOCTYPE HTML>
<html lang="zh-CN">
<head>
    <meta charset="${charset!"UTF-8"}">
    <title>表目录 - ${dbName?html}</title>
    <link rel="stylesheet" href="styles.css">
</head>
<body>
<div class="page">
    <div class="page-header">
        <h1>表目录</h1>
        <div class="breadcrumb">
            <a href="index.html">封面</a> &gt; 表目录
            &nbsp;|&nbsp; 数据库: ${dbName?html}
        </div>
    </div>

    <div class="catalog-summary">
        共 ${tableCount} 张表，点击表名查看列结构详情。
    </div>

    <table class="data catalog">
        <thead>
        <tr>
            <th style="width: 60px;">序号</th>
            <th>表名</th>
            <th>表说明</th>
        </tr>
        </thead>
        <tbody>
        <#list tables as t>
        <tr>
            <td class="seq">${t.seq}</td>
            <td class="tbl-name">
                <a href="${t.pageFile}">${t.name?html}</a>
            </td>
            <td class="comment">${t.comment?html}</td>
        </tr>
        </#list>
        </tbody>
    </table>
</div>
</body>
</html>