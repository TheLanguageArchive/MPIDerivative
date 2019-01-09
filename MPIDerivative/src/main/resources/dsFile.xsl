<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xs"
    version="2.0">
    
    <xsl:param name="file"/>
    <xsl:param name="mime"/>
        
        <xsl:template name="main">
            <xsl:message>File:
                <xsl:value-of select="$file"/>
            </xsl:message>
            <xsl:message>Mime:
                <xsl:value-of select="$mime"/>
            </xsl:message>
            <xsl:variable name="res">
            <foxml:datastreamVersion xmlns:foxml="info:fedora/fedora-system:def/foxml#" LABEL="{replace($file,'.*/','')}" MIMETYPE="{$mime}">
                <foxml:contentLocation TYPE="URL" REF="file:{$file}"/>
            </foxml:datastreamVersion>       
        </xsl:variable>
        <xsl:message>result[<xsl:copy-of select="$res"/>]</xsl:message>
        <xsl:copy-of select="$res"/>
        </xsl:template>
        
</xsl:stylesheet>