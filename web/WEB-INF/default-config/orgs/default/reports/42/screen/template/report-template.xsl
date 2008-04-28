<?xml version="1.0" encoding="us-ascii"?>
<xsl:stylesheet version="1.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:output method="html" encoding="US-ASCII"
		doctype-public="-//W3C//DTD HTML 4.01//EN"
		doctype-system="http://www.w3.org/TR/html4/strict.dtd" indent="yes" />

	<xsl:template match="/">
		<html>
			<head>
				<link rel="stylesheet" type="text/css"
					href="{/source/request/@context-path}/orgs/1/reports/9/stream/output/" />

				<title>
					<xsl:value-of select="/source/org/@name" />
					&gt; Suppliers &gt;
					<xsl:value-of
						select="/source/bills/account/supplier/@name" />
					&gt; Accounts &gt;
					<xsl:value-of
						select="/source/bills/account/@reference" />
					&gt; Bills
				</title>
			</head>
			<body>
				<p>
					<a
						href="{/source/request/@context-path}/orgs/{/source/org/@id}/reports/0/screen/output/">
						<xsl:value-of select="/source/org/@name" />
					</a>
					&gt;
					<a
						href="{/source/request/@context-path}/orgs/{/source/org/@id}/reports/35/screen/output/">
						<xsl:value-of select="'Suppliers'" />
					</a>
					&gt;
					<a
						href="{/source/request/@context-path}/orgs/{/source/org/@id}/reports/36/screen/output/?supplier-id={/source/bills/account/supplier/@id}">
						<xsl:value-of
							select="/source/bills/account/supplier/@name" />
					</a>
					&gt;
					<a
						href="{/source/request/@context-path}/orgs/{/source/org/@id}/reports/37/screen/output/?supplier-id={/source/bills/account/supplier/@id}">
						<xsl:value-of select="'Accounts'" />
					</a>
					&gt;
					<a
						href="{/source/request/@context-path}/orgs/{/source/org/@id}/reports/37/screen/output/?supplier-id={/source/bills/account/supplier/@id}">
						<xsl:value-of
							select="/source/bills/account/@reference" />
					</a>
					&gt;
					<xsl:value-of select="'Bills'" />

				</p>
				<br />
				<xsl:if test="//message">
					<ul>
						<xsl:for-each select="//message">
							<li>
								<xsl:value-of select="@description" />
							</li>
						</xsl:for-each>
					</ul>
				</xsl:if>
				<table>
					<caption>Bills</caption>
					<thead>
						<tr>
							<th>Id</th>
							<th>From</th>
							<th>To</th>
							<th>Net</th>
							<th>VAT</th>
						</tr>
					</thead>
					<tbody>
						<xsl:for-each select="/source/bills/bill">
							<tr>
								<td>
									<a
										href="{/source/request/@context-path}/orgs/{/source/org/@id}/reports/43/screen/output/?bill-id={@id}">
										<xsl:value-of select="@id" />
									</a>
								</td>
								<td>
									<xsl:value-of
										select="concat(day-start-date/@year, '-', day-start-date/@month, '-', day-start-date/@day)" />
								</td>
								<td>
									<xsl:value-of
										select="concat(day-finish-date/@year, '-', day-finish-date/@month, '-', day-finish-date/@day)" />
								</td>
								<td>
									<xsl:value-of select="@net" />
								</td>
								<td>
									<xsl:value-of select="@vat" />
								</td>
							</tr>
						</xsl:for-each>
					</tbody>
				</table>
			</body>
		</html>
	</xsl:template>
</xsl:stylesheet>