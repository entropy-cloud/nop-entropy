<!DOCTYPE html>
<html>
    <head>
        <title>Stock Prices</title>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
        <meta http-equiv="Content-Style-Type" content="text/css"/>
        <meta http-equiv="Content-Script-Type" content="text/javascript"/>
        <link rel="shortcut icon" href="/images/favicon.ico"/>
        <link rel="stylesheet" type="text/css" href="/css/style.css" media="all"/>
        <script type="text/javascript" src="/js/util.js"></script>
        <style type="text/css">
            body {
            color: #333333;
            line-height: 150%;
            }

            thead {
            font-weight: bold;
            background-color: #CCCCCC;
            }

            .odd {
            background-color: #FFCCCC;
            }

            .even {
            background-color: #CCCCFF;
            }

            .minus {
            color: #FF0000;
            }

        </style>

    </head>

    <body>

        <h1>Stock Prices</h1>

        <table>
            <thead>
                <tr>
                    <th>#</th>
                    <th>symbol</th>
                    <th>name</th>
                    <th>price</th>
                    <th>change</th>
                    <th>ratio</th>
                </tr>
            </thead>
            <tbody>
                <c:for var="item" items="${items}" index="index">
                    <tr class="${index % 2 == 1 ? 'even' : 'odd'}">
                        <td>${index + 1}</td>
                        <td>
                            <a href="/stocks/${ item.symbol }">${item.symbol}</a>
                        </td>
                        <td>
                            <a href="${ item.url }">${item.name}</a>
                        </td>
                        <td>
                            <strong>${ item.price }</strong>
                        </td>

                        <c:choose>
                            <when test="${item.change &lt; 0}">
                                <td class="minus">${item.change}</td>
                                <td class="minus">${item.ratio}</td>
                            </when>
                            <otherwise>
                                <td>${item.change}</td>
                                <td>${item.ratio}</td>
                            </otherwise>
                        </c:choose>
                    </tr>
                </c:for>
            </tbody>
        </table>
    </body>
</html>
