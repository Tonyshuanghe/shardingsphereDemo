package com.example.dmSupport.parser;

import com.example.constant.CommonConstants;
import org.apache.shardingsphere.sql.parser.api.parser.SQLLexer;
import org.apache.shardingsphere.sql.parser.api.parser.SQLParser;
import org.apache.shardingsphere.sql.parser.oracle.parser.OracleLexer;
import org.apache.shardingsphere.sql.parser.oracle.parser.OracleParser;
import org.apache.shardingsphere.sql.parser.spi.SQLDialectParserFacade;

/**
 * DmParserFacade
 *
 * @author ygm
 */
public class DmParserFacade implements SQLDialectParserFacade {

    @Override
    public Class<? extends SQLLexer> getLexerClass() {
        return OracleLexer.class;
    }

    @Override
    public Class<? extends SQLParser> getParserClass() {
        return OracleParser.class;
    }

    @Override
    public String getDatabaseType() {
        return CommonConstants.DM;
    }
}
