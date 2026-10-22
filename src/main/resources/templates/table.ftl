<!DOCTYPE HTML>
<html lang="zh-CN">
<head>
    <meta charset="${charset!"UTF-8"}">
    <title>${table.name?html} - ${dbName?html}</title>
    <link rel="stylesheet" href="styles.css">
</head>
<body>
<div class="page">
    <div class="page-header">
        <h1>表结构详情</h1>
        <div class="breadcrumb">
            <a href="index.html">封面</a> &gt;
            <a href="catalog.html">表目录</a> &gt;
            <strong>${table.name?html}</strong>
        </div>
    </div>

    <div class="table-header">
        <h2>${table.name?html}</h2>
        <div class="table-comment">${table.comment?html}</div>
    </div>

    <div class="section-title">列信息（共 ${table.columnCount} 列）</div>

    <table class="data">
        <thead>
        <tr>
            <th>序号</th>
            <th>列名</th>
            <th>数据类型</th>
            <th>长度</th>
            <th>主键</th>
            <th>自增</th>
            <th>允许空</th>
            <th>列说明</th>
        </tr>
        </thead>
        <tbody>
        <#list table.columns as c>
        <tr>
            <td class="seq">${c.seq}</td>
            <td class="col-name">${c.name?html}</td>
            <td class="col-type">${c.dataType?html}</td>
            <td class="seq">${c.length?html}</td>
            <td class="pk">
                <#if c.isPk>
                    <span class="pk-mark"><#if (c.pkSeq > 1)>PK(${c.pkSeq})<#else>PK</#if></span>
                <#else>
                    <span class="no-mark">—</span>
                </#if>
            </td>
            <td class="auto-inc">
                <#if c.isAutoIncrement>
                    <span class="auto-inc-mark">Y</span>
                <#else>
                    <span class="no-mark">—</span>
                </#if>
            </td>
            <td class="nullable">${c.nullable?then("Y", "N")}</td>
            <td class="comment">${c.comment?html}</td>
        </tr>
        </#list>
        </tbody>
    </table>

    <div style="margin-top: 32px; text-align: right; font-size: 12px; color: #999;">
        数据库：${dbType} · 连接：${connInfo?html} · 生成时间：${generatedAt}
    </div>
</div>
</body>
</html>