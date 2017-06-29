/*
 * Copyright 1999-2017 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.druid.sql.dialect.oracle.parser;

import java.math.BigInteger;
import java.sql.Savepoint;

import com.alibaba.druid.sql.ast.*;
import com.alibaba.druid.sql.ast.SQLKeep.DenseRank;
import com.alibaba.druid.sql.ast.expr.SQLAggregateExpr;
import com.alibaba.druid.sql.ast.expr.SQLAggregateOption;
import com.alibaba.druid.sql.ast.expr.SQLBinaryOpExpr;
import com.alibaba.druid.sql.ast.expr.SQLBinaryOperator;
import com.alibaba.druid.sql.ast.expr.SQLCharExpr;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.expr.SQLIntegerExpr;
import com.alibaba.druid.sql.ast.expr.SQLMethodInvokeExpr;
import com.alibaba.druid.sql.ast.expr.SQLNumberExpr;
import com.alibaba.druid.sql.ast.expr.SQLNumericLiteralExpr;
import com.alibaba.druid.sql.ast.expr.SQLPropertyExpr;
import com.alibaba.druid.sql.ast.expr.SQLSequenceExpr;
import com.alibaba.druid.sql.ast.expr.SQLTimestampExpr;
import com.alibaba.druid.sql.ast.expr.SQLUnaryExpr;
import com.alibaba.druid.sql.ast.expr.SQLUnaryOperator;
import com.alibaba.druid.sql.ast.expr.SQLVariantRefExpr;
import com.alibaba.druid.sql.ast.statement.*;
import com.alibaba.druid.sql.dialect.oracle.ast.OracleDataTypeIntervalDay;
import com.alibaba.druid.sql.dialect.oracle.ast.OracleDataTypeIntervalYear;
import com.alibaba.druid.sql.dialect.oracle.ast.OracleDataTypeTimestamp;
import com.alibaba.druid.sql.dialect.oracle.ast.OracleSegmentAttributes;
import com.alibaba.druid.sql.dialect.oracle.ast.clause.OracleLobStorageClause;
import com.alibaba.druid.sql.dialect.oracle.ast.clause.OracleStorageClause;
import com.alibaba.druid.sql.dialect.oracle.ast.clause.OracleStorageClause.FlashCacheType;
import com.alibaba.druid.sql.dialect.oracle.ast.expr.*;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleCheck;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleConstraint;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleConstraint.Initially;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleForeignKey;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OraclePrimaryKey;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleUnique;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleUsingIndexClause;
import com.alibaba.druid.sql.parser.Lexer;
import com.alibaba.druid.sql.parser.ParserException;
import com.alibaba.druid.sql.parser.SQLExprParser;
import com.alibaba.druid.sql.parser.Token;
import com.alibaba.druid.util.JdbcConstants;

public class OracleExprParser extends SQLExprParser {






    public boolean                allowStringAdditive = false;

    public static final String[] AGGREGATE_FUNCTIONS = {
                                                          "AVG", // 
                                                          "CORR", // 
                                                          "COVAR_POP", //
                                                          "COVAR_SAMP", // 
                                                          "COUNT", // 
                                                          "CUME_DIST", // 
                                                          "DENSE_RANK", // 
                                                          "FIRST", // 
                                                          "FIRST_VALUE", // 
                                                          "LAG", // 
                                                          "LAST", // 
                                                          "LAST_VALUE", // 
                                                          "LISTAGG",
                                                          "LEAD", // 
                                                          "MAX",  // 
                                                          "MIN", // 
                                                          "NTILE", // 
                                                          "PERCENT_RANK",  // 
                                                          "PERCENTILE_CONT",  // 
                                                          "PERCENTILE_DISC",  // 
                                                          "RANK", // 
                                                          "RATIO_TO_REPORT", // 
                                                          "REGR_SLOPE", // 
                                                          "REGR_INTERCEPT",  // 
                                                          "REGR_COUNT",  // 
                                                          "REGR_R2", // 
                                                          "REGR_AVGX",  // 
                                                          "REGR_AVGY",  // 
                                                          "REGR_SXX",  // 
                                                          "REGR_SYY",  // 
                                                          "REGR_SXY", // 
                                                          "ROW_NUMBER",  // 
                                                          "STDDEV",  // 
                                                          "STDDEV_POP",  // 
                                                          "STDDEV_SAMP", // 
                                                          "SUM", // 
                                                          "VAR_POP", // 
                                                          "VAR_SAMP", // 
                                                          "VARIANCE", // 
                                                          "WM_CONCAT"
                                                          };

    public OracleExprParser(Lexer lexer){
        super(lexer);
        this.aggregateFunctions = AGGREGATE_FUNCTIONS;
        this.dbType = JdbcConstants.ORACLE;
    }

    public OracleExprParser(String text){
        this(new OracleLexer(text));
        this.lexer.nextToken();
        this.dbType = JdbcConstants.ORACLE;
    }
    
    protected boolean isCharType(String dataTypeName) {
        return "varchar2".equalsIgnoreCase(dataTypeName) //
                || "nvarchar2".equalsIgnoreCase(dataTypeName) //
                || "char".equalsIgnoreCase(dataTypeName) //
               || "varchar".equalsIgnoreCase(dataTypeName) //
               || "nchar".equalsIgnoreCase(dataTypeName) //
               || "nvarchar".equalsIgnoreCase(dataTypeName) //
        //
        ;
    }

    public SQLDataType parseDataType(boolean restrict) {

        if (lexer.token() == Token.CONSTRAINT || lexer.token() == Token.COMMA) {
            return null;
        }

        if (lexer.token() == Token.DEFAULT || lexer.token() == Token.NOT || lexer.token() == Token.NULL) {
            return null;
        }

        if (lexer.token() == Token.INTERVAL) {
            lexer.nextToken();
            if (identifierEquals("YEAR")) {
                lexer.nextToken();
                OracleDataTypeIntervalYear interval = new OracleDataTypeIntervalYear();

                if (lexer.token() == Token.LPAREN) {
                    lexer.nextToken();
                    interval.addArgument(this.expr());
                    accept(Token.RPAREN);
                }

                accept(Token.TO);
                acceptIdentifier("MONTH");

                return interval;
            } else {
                acceptIdentifier("DAY");
                OracleDataTypeIntervalDay interval = new OracleDataTypeIntervalDay();
                if (lexer.token() == Token.LPAREN) {
                    lexer.nextToken();
                    interval.addArgument(this.expr());
                    accept(Token.RPAREN);
                }

                accept(Token.TO);
                acceptIdentifier("SECOND");

                if (lexer.token() == Token.LPAREN) {
                    lexer.nextToken();
                    interval.getFractionalSeconds().add(this.expr());
                    accept(Token.RPAREN);
                }

                return interval;
            }
        }

        String typeName;
        if (lexer.token() == Token.EXCEPTION) {
            typeName = "EXCEPTION";
            lexer.nextToken();
        } else if (identifierEquals("LONG")) {
            lexer.nextToken();

            if (identifierEquals("RAW")) {
                lexer.nextToken();
                typeName = "LONG RAW";
            } else {
                typeName = "LONG";
            }
        } else {
            SQLName typeExpr = name();
            typeName = typeExpr.toString();
        }
        
        if ("TIMESTAMP".equalsIgnoreCase(typeName)) {
            OracleDataTypeTimestamp timestamp = new OracleDataTypeTimestamp();
            
            if (lexer.token() == Token.LPAREN) {
                lexer.nextToken();
                timestamp.addArgument(this.expr());
                accept(Token.RPAREN);
            }
            
            if (lexer.token() == Token.WITH) {
                lexer.nextToken();
                
                if (identifierEquals("LOCAL")) {
                    lexer.nextToken();
                    timestamp.setWithLocalTimeZone(true);
                } else {
                    timestamp.setWithTimeZone(true);
                }
                
                acceptIdentifier("TIME");
                acceptIdentifier("ZONE");
            }
            
            return timestamp;
        }
        
        if (isCharType(typeName)) {
            SQLCharacterDataType charType = new SQLCharacterDataType(typeName);

            if (lexer.token() == Token.LPAREN) {
                lexer.nextToken();

                charType.addArgument(this.expr());

                if (identifierEquals("CHAR")) {
                    lexer.nextToken();
                    charType.setCharType(SQLCharacterDataType.CHAR_TYPE_CHAR);
                } else if (identifierEquals("BYTE")) {
                    lexer.nextToken();
                    charType.setCharType(SQLCharacterDataType.CHAR_TYPE_BYTE);
                }

                accept(Token.RPAREN);
            } else if (restrict) {
                accept(Token.LPAREN);
            }
            
            return parseCharTypeRest(charType);
        }
        
        if (lexer.token() == Token.PERCENT) {
            lexer.nextToken();
            if (identifierEquals("TYPE")) {
                lexer.nextToken();
                typeName += "%TYPE";
            } else if (identifierEquals("ROWTYPE")) {
                lexer.nextToken();
                typeName += "%ROWTYPE";
            } else {
                throw new ParserException("syntax error : " + lexer.token() + " " + lexer.stringVal());
            }
        }


        SQLDataType dataType = new SQLDataTypeImpl(typeName);        
        return parseDataTypeRest(dataType);
    }

    public SQLExpr primary() {
        final Token tok = lexer.token();

        SQLExpr sqlExpr = null;
        switch (tok) {
            case SYSDATE:
                lexer.nextToken();
                OracleSysdateExpr sysdate = new OracleSysdateExpr();
                if (lexer.token() == Token.MONKEYS_AT) {
                    lexer.nextToken();
                    accept(Token.BANG);
                    sysdate.setOption("!");
                }
                sqlExpr = sysdate;
                return primaryRest(sqlExpr);
            case PRIOR:
                lexer.nextToken();
                sqlExpr = expr();
                sqlExpr = new SQLUnaryExpr(SQLUnaryOperator.Prior, sqlExpr);
                return primaryRest(sqlExpr);
            case COLON:
                lexer.nextToken();
                if (lexer.token() == Token.LITERAL_INT) {
                    String name = ":" + lexer.numberString();
                    lexer.nextToken();
                    return new SQLVariantRefExpr(name);
                } else if (lexer.token() == Token.IDENTIFIER) {
                    String name = lexer.stringVal();
                    if (name.charAt(0) == 'B' || name.charAt(0) == 'b') {
                        lexer.nextToken();
                        return new SQLVariantRefExpr(":" + name);
                    }
                    throw new ParserException("syntax error : " + lexer.token() + " " + lexer.stringVal());
                } else {
                    throw new ParserException("syntax error : " + lexer.token());
                }
            case LITERAL_ALIAS:
                String alias = '"' + lexer.stringVal() + '"';
                lexer.nextToken();
                return primaryRest(new SQLIdentifierExpr(alias));
            case BINARY_FLOAT:
                OracleBinaryFloatExpr floatExpr = new OracleBinaryFloatExpr();
                floatExpr.setValue(Float.parseFloat(lexer.numberString()));
                lexer.nextToken();
                return primaryRest(floatExpr);
            case BINARY_DOUBLE:
                OracleBinaryDoubleExpr doubleExpr = new OracleBinaryDoubleExpr();
                doubleExpr.setValue(Double.parseDouble(lexer.numberString()));

                lexer.nextToken();
                return primaryRest(doubleExpr);
            case TABLE:
                lexer.nextToken();
                return primaryRest(new SQLIdentifierExpr("TABLE"));
            case PLUS:
                lexer.nextToken();
                switch (lexer.token()) {
                    case LITERAL_INT:
                        sqlExpr = new SQLIntegerExpr(lexer.integerValue());
                        lexer.nextToken();
                        break;
                    case LITERAL_FLOAT:
                        sqlExpr = new SQLNumberExpr(lexer.decimalValue());
                        lexer.nextToken();
                        break;
                    case BINARY_FLOAT:
                        sqlExpr = new OracleBinaryFloatExpr(Float.parseFloat(lexer.numberString()));
                        lexer.nextToken();
                        break;
                    case BINARY_DOUBLE:
                        sqlExpr = new OracleBinaryDoubleExpr(Double.parseDouble(lexer.numberString()));
                        lexer.nextToken();
                        break;
                    case LPAREN:
                        lexer.nextToken();
                        sqlExpr = expr();
                        accept(Token.RPAREN);
                        sqlExpr = new SQLUnaryExpr(SQLUnaryOperator.Plus, sqlExpr);
                        break;
                    case IDENTIFIER: {
                        sqlExpr = expr();
                        sqlExpr = new SQLUnaryExpr(SQLUnaryOperator.Plus, sqlExpr);
                        break;
                    }
                    default:
                        throw new ParserException("TODO " + lexer.info());
                }
                return primaryRest(sqlExpr);
            case SUB:
                lexer.nextToken();
                switch (lexer.token()) {
                    case LITERAL_INT:
                        Number integerValue = lexer.integerValue();
                        if (integerValue instanceof Integer) {
                            int intVal = ((Integer) integerValue).intValue();
                            if (intVal == Integer.MIN_VALUE) {
                                integerValue = Long.valueOf(((long) intVal) * -1);
                            } else {
                                integerValue = Integer.valueOf(intVal * -1);
                            }
                        } else if (integerValue instanceof Long) {
                            long longVal = ((Long) integerValue).longValue();
                            if (longVal == 2147483648L) {
                                integerValue = Integer.valueOf((int) (((long) longVal) * -1));
                            } else {
                                integerValue = Long.valueOf(longVal * -1);
                            }
                        } else {
                            integerValue = ((BigInteger) integerValue).negate();
                        }
                        sqlExpr = new SQLIntegerExpr(integerValue);
                        lexer.nextToken();
                        break;
                    case LITERAL_FLOAT:
                        sqlExpr = new SQLNumberExpr(lexer.decimalValue().negate());
                        lexer.nextToken();
                        break;
                    case BINARY_FLOAT:
                        sqlExpr = new OracleBinaryFloatExpr(Float.parseFloat(lexer.numberString()) * -1);
                        lexer.nextToken();
                        break;
                    case BINARY_DOUBLE:
                        sqlExpr = new OracleBinaryDoubleExpr(Double.parseDouble(lexer.numberString()) * -1);
                        lexer.nextToken();
                        break;
                    case VARIANT:
                    case QUES:
                    case IDENTIFIER:
                        sqlExpr = expr();
                        sqlExpr = new SQLUnaryExpr(SQLUnaryOperator.Negative, sqlExpr);
                        break;
                    case LPAREN:
                        lexer.nextToken();
                        sqlExpr = expr();
                        accept(Token.RPAREN);
                        sqlExpr = new SQLUnaryExpr(SQLUnaryOperator.Negative, sqlExpr);
                        break;
                    default:
                        throw new ParserException("TODO " + lexer.token());
                }
                return primaryRest(sqlExpr);
                
           case CURSOR:
                lexer.nextToken();
                accept(Token.LPAREN);

                SQLSelect select = createSelectParser().select();
                OracleCursorExpr cursorExpr = new OracleCursorExpr(select);

                accept(Token.RPAREN);

                sqlExpr = cursorExpr;
                return  primaryRest(sqlExpr);
           case MODEL:
           case PCTFREE:
           case INITRANS:
           case MAXTRANS:
           case SEGMENT:
           case CREATION:
           case IMMEDIATE:
           case DEFERRED:
           case STORAGE:
           case NEXT:
           case MINEXTENTS:
           case MAXEXTENTS:
           case MAXSIZE:
           case PCTINCREASE:
           case FLASH_CACHE:
           case CELL_FLASH_CACHE:
           case NONE:
           case LOB:
           case STORE:
           case ROW:
           case CHUNK:
           case CACHE:
           case NOCACHE:
           case LOGGING:
           case NOCOMPRESS:
           case KEEP_DUPLICATES:
           case EXCEPTIONS:
           case PURGE:
           case OUTER:
               sqlExpr = new SQLIdentifierExpr(lexer.stringVal());
               lexer.nextToken();
               return  primaryRest(sqlExpr);
            default:
                return super.primary();
        }
    }
    
    @Override
    protected SQLExpr methodRest(SQLExpr expr, boolean acceptLPAREN) {
        if (acceptLPAREN) {
            accept(Token.LPAREN);
        }
        
        if (lexer.token() == Token.PLUS) {
            lexer.nextToken();
            accept(Token.RPAREN);
            return new OracleOuterExpr(expr);
        }
        
        if (expr instanceof SQLIdentifierExpr) {
            String methodName = ((SQLIdentifierExpr) expr).getName();
            SQLMethodInvokeExpr methodExpr = new SQLMethodInvokeExpr(methodName);
            if ("trim".equalsIgnoreCase(methodName)) {
                if (identifierEquals("LEADING") //
                        || identifierEquals("TRAILING") //
                        || identifierEquals("BOTH")
                        ) {
                    methodExpr.putAttribute("trim_option", lexer.stringVal());
                    lexer.nextToken();
                }
                
                if (lexer.token() != Token.FROM) {
                    SQLExpr trim_character = this.primary();
                    trim_character.setParent(methodExpr);
                    if (trim_character instanceof SQLCharExpr) {
                        methodExpr.putAttribute("trim_character", trim_character);
                    } else {
                        methodExpr.getParameters().add(trim_character);
                    }
                }
                
                if (lexer.token() == Token.FROM) {
                    lexer.nextToken();
                    SQLExpr trim_source = this.expr();
                    methodExpr.addParameter(trim_source);
                }
                
                accept(Token.RPAREN);
                return primaryRest(methodExpr);
            } else if ("treat".equalsIgnoreCase(methodName)) {
                OracleTreatExpr treatExpr = new OracleTreatExpr();

                treatExpr.setExpr(this.expr());

                accept(Token.AS);

                if (identifierEquals("REF")) {
                    treatExpr.setRef(true);
                    lexer.nextToken();
                }

                treatExpr.setType(this.expr());
                accept(Token.RPAREN);

                return primaryRest(treatExpr);
            }
        }
        
        return super.methodRest(expr, false);
    }

    public SQLExpr primaryRest(SQLExpr expr) {
        if (expr.getClass() == SQLIdentifierExpr.class) {
            String ident = ((SQLIdentifierExpr)expr).getName();
            if ("TIMESTAMP".equalsIgnoreCase(ident)) {
                if (lexer.token() != Token.LITERAL_ALIAS && lexer.token() != Token.LITERAL_CHARS) {
                    return new SQLIdentifierExpr("TIMESTAMP");
                }

                SQLTimestampExpr timestamp = new SQLTimestampExpr();

                String literal = lexer.stringVal();
                timestamp.setLiteral(literal);
                accept(Token.LITERAL_CHARS);

                if (identifierEquals("AT")) {
                    lexer.nextToken();
                    acceptIdentifier("TIME");
                    acceptIdentifier("ZONE");

                    String timezone = lexer.stringVal();
                    timestamp.setTimeZone(timezone);
                    accept(Token.LITERAL_CHARS);
                }

                
                return primaryRest(timestamp);     
            }
        }
        if (lexer.token() == Token.IDENTIFIER && expr instanceof SQLNumericLiteralExpr) {
            String ident = lexer.stringVal();
            
            if (ident.length() == 1) {
                char unit = ident.charAt(0);
                switch (unit) {
                    case 'K':
                    case 'M':
                    case 'G':
                    case 'T':
                    case 'P':
                    case 'E':
                    case 'k':
                    case 'm':
                    case 'g':
                    case 't':
                    case 'p':
                    case 'e':
                        expr = new OracleSizeExpr(expr, OracleSizeExpr.Unit.valueOf(ident.toUpperCase()));
                        lexer.nextToken();
                        break;
                    default:
                    break;
                }
            }
        }
        
        if (lexer.token() == Token.DOTDOT) {
            lexer.nextToken();
            SQLExpr upBound = expr();
            
            return new OracleRangeExpr(expr, upBound);
        }
        
        if (lexer.token() == Token.MONKEYS_AT) {
            lexer.nextToken();

            OracleDbLinkExpr dblink = new OracleDbLinkExpr();
            dblink.setExpr(expr);

            if (lexer.token() == Token.BANG) {
                dblink.setDbLink("!");
                lexer.nextToken();
            } else {
                String link = lexer.stringVal();
                accept(Token.IDENTIFIER);
                dblink.setDbLink(link);
            }

            expr = dblink;
        }
        
        if (identifierEquals("DAY") || identifierEquals("YEAR")) {
            Lexer.SavePoint savePoint = lexer.mark();
            
            String name = lexer.stringVal();
            lexer.nextToken();
            
            if (lexer.token() == Token.COMMA) {
                lexer.reset(savePoint);
                return expr;
            }
            
            OracleIntervalExpr interval = new OracleIntervalExpr();
            interval.setValue(expr);
            OracleIntervalType type = OracleIntervalType.valueOf(name.toUpperCase());
            interval.setType(type);
            
            if (lexer.token() == Token.LPAREN) {
                lexer.nextToken();
                if (lexer.token() != Token.LITERAL_INT) {
                    throw new ParserException("syntax error");
                }
                interval.setPrecision(lexer.integerValue().intValue());
                lexer.nextToken();
                accept(Token.RPAREN);
            }

            if (lexer.token() == Token.TO) {
                lexer.nextToken();
            } else {
                lexer.reset(savePoint);
                return expr;
            }

            if (identifierEquals("SECOND")) {
                lexer.nextToken();
                interval.setToType(OracleIntervalType.SECOND);
                if (lexer.token() == Token.LPAREN) {
                    lexer.nextToken();
                    if (lexer.token() != Token.LITERAL_INT) {
                        throw new ParserException("syntax error");
                    }
                    interval.setFactionalSecondsPrecision(lexer.integerValue().intValue());
                    lexer.nextToken();
                    accept(Token.RPAREN);
                }
            } else {
                interval.setToType(OracleIntervalType.MONTH);
                lexer.nextToken();
            }
            
            expr = interval;
        }
        
        if (identifierEquals("AT")) {
            char markChar = lexer.current();
            int markBp = lexer.bp();
            lexer.nextToken();
            if (identifierEquals("LOCAL")) {
                lexer.nextToken();
                expr = new OracleDatetimeExpr(expr, new SQLIdentifierExpr("LOCAL"));
            } else {
                if (identifierEquals("TIME")) {
                    lexer.nextToken();
                } else {
                    lexer.reset(markBp, markChar, Token.IDENTIFIER);
                    return expr;
                }
                acceptIdentifier("ZONE");
                
                SQLExpr timeZone = primary();
                expr = new OracleDatetimeExpr(expr, timeZone);
            }
        }
        
        SQLExpr restExpr = super.primaryRest(expr);
        
        if (restExpr != expr && restExpr instanceof SQLMethodInvokeExpr) {
            SQLMethodInvokeExpr methodInvoke = (SQLMethodInvokeExpr) restExpr;
            if (methodInvoke.getParameters().size() == 1) {
                SQLExpr paramExpr = methodInvoke.getParameters().get(0);
                if (paramExpr instanceof SQLIdentifierExpr && "+".equals(((SQLIdentifierExpr) paramExpr).getName())) {
                    OracleOuterExpr outerExpr = new OracleOuterExpr();
                    if (methodInvoke.getOwner() == null) {
                        outerExpr.setExpr(new SQLIdentifierExpr(methodInvoke.getMethodName()));
                    } else {
                        outerExpr.setExpr(new SQLPropertyExpr(methodInvoke.getOwner(), methodInvoke.getMethodName()));
                    }
                    return outerExpr;
                }
            }
        }
        
        return restExpr;
    }

    protected SQLExpr dotRest(SQLExpr expr) {
        if (lexer.token() == Token.LITERAL_ALIAS) {
            String name = '"' + lexer.stringVal() + '"';
            lexer.nextToken();
            expr = new SQLPropertyExpr(expr, name);
            
            if (lexer.token() == Token.DOT) {
                lexer.nextToken();
                expr = dotRest(expr);
            }

            return expr;
        }
        
        if (identifierEquals("NEXTVAL")) {
            if (expr instanceof SQLIdentifierExpr) {
                SQLIdentifierExpr identExpr = (SQLIdentifierExpr) expr;
                SQLSequenceExpr seqExpr = new SQLSequenceExpr(identExpr, SQLSequenceExpr.Function.NextVal);
                lexer.nextToken();
                return seqExpr;
            }
        } else if (identifierEquals("CURRVAL")) {
            if (expr instanceof SQLIdentifierExpr) {
                SQLIdentifierExpr identExpr = (SQLIdentifierExpr) expr;
                SQLSequenceExpr seqExpr = new SQLSequenceExpr(identExpr, SQLSequenceExpr.Function.CurrVal);
                lexer.nextToken();
                return seqExpr;
            }
        }

        return super.dotRest(expr);
    }

    protected SQLAggregateExpr parseAggregateExpr(String methodName) {
        methodName = methodName.toUpperCase();
        
        SQLAggregateExpr aggregateExpr;
        if (lexer.token() == Token.UNIQUE) {
            aggregateExpr = new SQLAggregateExpr(methodName, SQLAggregateOption.UNIQUE);
            lexer.nextToken();
        } else if (lexer.token() == (Token.ALL)) {
            aggregateExpr = new SQLAggregateExpr(methodName, SQLAggregateOption.ALL);
            lexer.nextToken();
        } else if (lexer.token() == (Token.DISTINCT)) {
            aggregateExpr = new SQLAggregateExpr(methodName, SQLAggregateOption.DISTINCT);
            lexer.nextToken();
        } else {
            aggregateExpr = new SQLAggregateExpr(methodName);
        }
        exprList(aggregateExpr.getArguments(), aggregateExpr);

        if (lexer.stringVal().equalsIgnoreCase("IGNORE")) {
            lexer.nextToken();
            identifierEquals("NULLS");
            aggregateExpr.setIgnoreNulls(true);
        }

        accept(Token.RPAREN);
        
        if (identifierEquals("WITHIN")) {
            lexer.nextToken();
            accept(Token.GROUP);
            accept(Token.LPAREN);
            SQLOrderBy withinGroup = this.parseOrderBy();
            aggregateExpr.setWithinGroup(withinGroup);
            accept(Token.RPAREN);
        }
        
        if (identifierEquals("KEEP")) {
            lexer.nextToken();
            
            SQLKeep keep = new SQLKeep();
            accept(Token.LPAREN);
            acceptIdentifier("DENSE_RANK");
            if (identifierEquals("FIRST")) {
                lexer.nextToken();
                keep.setDenseRank(DenseRank.FIRST);
            } else {
                acceptIdentifier("LAST");
                keep.setDenseRank(DenseRank.LAST);
            }
            
            SQLOrderBy orderBy = this.parseOrderBy();
            keep.setOrderBy(orderBy);
            
            aggregateExpr.setKeep(keep);
            
            accept(Token.RPAREN);
        }

        if (lexer.token() == Token.OVER) {
            OracleAnalytic over = new OracleAnalytic();

            lexer.nextToken();
            accept(Token.LPAREN);

            if (lexer.token() == Token.PARTITION) {
                lexer.nextToken();
                accept(Token.BY);

                if (lexer.token() == (Token.LPAREN)) {
                    lexer.nextToken();
                    exprList(over.getPartitionBy(), over);
                    accept(Token.RPAREN);
                } else {
                    exprList(over.getPartitionBy(), over);
                }
            }

            over.setOrderBy(parseOrderBy());
            if (over.getOrderBy() != null) {
                OracleAnalyticWindowing windowing = null;
                if (lexer.stringVal().equalsIgnoreCase("ROWS")) {
                    lexer.nextToken();
                    windowing = new OracleAnalyticWindowing();
                    windowing.setType(OracleAnalyticWindowing.Type.ROWS);
                } else if (lexer.stringVal().equalsIgnoreCase("RANGE")) {
                    lexer.nextToken();
                    windowing = new OracleAnalyticWindowing();
                    windowing.setType(OracleAnalyticWindowing.Type.RANGE);
                }

                if (windowing != null) {
                    if (lexer.stringVal().equalsIgnoreCase("CURRENT")) {
                        lexer.nextToken();
                        if (lexer.stringVal().equalsIgnoreCase("ROW")) {
                            lexer.nextToken();
                            windowing.setExpr(new SQLIdentifierExpr("CURRENT ROW"));
                            over.setWindowing(windowing);
                        }
                        throw new ParserException("syntax error");
                    }
                    if (lexer.stringVal().equalsIgnoreCase("UNBOUNDED")) {
                        lexer.nextToken();
                        if (lexer.stringVal().equalsIgnoreCase("PRECEDING")) {
                            lexer.nextToken();
                            windowing.setExpr(new SQLIdentifierExpr("UNBOUNDED PRECEDING"));
                        } else {
                            throw new ParserException("syntax error");
                        }
                    }

                    over.setWindowing(windowing);
                }
            }

            accept(Token.RPAREN);

            aggregateExpr.setOver(over);
        }
        return aggregateExpr;
    }

    @SuppressWarnings("unused")
    private OracleIntervalType parseIntervalType() {
        String currentTokenUpperValue = lexer.stringVal();
        lexer.nextToken();

        if (currentTokenUpperValue.equals("YEAR")) {
            return OracleIntervalType.YEAR;
        }
        if (currentTokenUpperValue.equals("MONTH")) {
            return OracleIntervalType.MONTH;
        }
        if (currentTokenUpperValue.equals("HOUR")) {
            return OracleIntervalType.HOUR;
        }
        if (currentTokenUpperValue.equals("MINUTE")) {
            return OracleIntervalType.MINUTE;
        }
        if (currentTokenUpperValue.equals("SECOND")) {
            return OracleIntervalType.SECOND;
        }
        throw new ParserException("syntax error");
    }

    @Override
    public OracleSelectParser createSelectParser() {
        return new OracleSelectParser(this);
    }

    protected SQLExpr parseInterval() {
        accept(Token.INTERVAL);
        
        OracleIntervalExpr interval = new OracleIntervalExpr();
        if (lexer.token() != Token.LITERAL_CHARS) {
            return new SQLIdentifierExpr("INTERVAL");
        }
        interval.setValue(new SQLCharExpr(lexer.stringVal()));
        lexer.nextToken();

        
        OracleIntervalType type = OracleIntervalType.valueOf(lexer.stringVal());
        interval.setType(type);
        lexer.nextToken();
        
        if (lexer.token() == Token.LPAREN) {
            lexer.nextToken();
            if (lexer.token() != Token.LITERAL_INT) {
                throw new ParserException("syntax error");
            }
            interval.setPrecision(lexer.integerValue().intValue());
            lexer.nextToken();
            
            if (lexer.token() == Token.COMMA) {
                lexer.nextToken();
                if (lexer.token() != Token.LITERAL_INT) {
                    throw new ParserException("syntax error");
                }
                interval.setFactionalSecondsPrecision(lexer.integerValue().intValue());
                lexer.nextToken();
            }
            accept(Token.RPAREN);
        }
        
        if (lexer.token() == Token.TO) {
            lexer.nextToken();
            if (identifierEquals("SECOND")) {
                lexer.nextToken();
                interval.setToType(OracleIntervalType.SECOND);
                if (lexer.token() == Token.LPAREN) {
                    lexer.nextToken();
                    if (lexer.token() != Token.LITERAL_INT) {
                        throw new ParserException("syntax error");
                    }
                    interval.setToFactionalSecondsPrecision(lexer.integerValue().intValue());
                    lexer.nextToken();
                    accept(Token.RPAREN);
                }
            } else {
                interval.setToType(OracleIntervalType.MONTH);
                lexer.nextToken();
            }
        }
        
        return interval;    
    }
    
    public SQLExpr relationalRest(SQLExpr expr) {
        if (lexer.token() == Token.IS) {
            lexer.nextToken();
            
            if (lexer.token() == Token.NOT) {
                lexer.nextToken();
                SQLExpr rightExpr = primary();
                expr = new SQLBinaryOpExpr(expr, SQLBinaryOperator.IsNot, rightExpr, getDbType());
            } else if (identifierEquals("A")) {
                lexer.nextToken();
                accept(Token.SET);
                expr = new OracleIsSetExpr(expr);
            } else {
                SQLExpr rightExpr = primary();
                expr = new SQLBinaryOpExpr(expr, SQLBinaryOperator.Is, rightExpr, getDbType());
            }
            
            return expr;
        }
        return super.relationalRest(expr);
    }
    
    
    public SQLName name() {
        SQLName name = super.name();
        
        if (lexer.token() == Token.MONKEYS_AT) {
            lexer.nextToken();
            if (lexer.token() != Token.IDENTIFIER) {
                throw new ParserException("syntax error, expect identifier, but " + lexer.token());
            }
            OracleDbLinkExpr dbLink = new OracleDbLinkExpr();
            dbLink.setExpr(name);
            dbLink.setDbLink(lexer.stringVal());
            lexer.nextToken();
            return dbLink;
        }
        
        return name;
    }
    
    public SQLExpr equalityRest(SQLExpr expr) {
        SQLExpr rightExp;
        if (lexer.token() == Token.EQ) {
            lexer.nextToken();
            
            if (lexer.token() == Token.GT) {
                lexer.nextToken();
                rightExp = expr();
                String argumentName = ((SQLIdentifierExpr) expr).getName();
                return new OracleArgumentExpr(argumentName, rightExp);
            }
            
            rightExp = shift();

            rightExp = equalityRest(rightExp);

            expr = new SQLBinaryOpExpr(expr, SQLBinaryOperator.Equality, rightExp, getDbType());
        } else if (lexer.token() == Token.BANGEQ || lexer.token() == Token.CARETEQ) {
            lexer.nextToken();
            rightExp = shift();

            rightExp = equalityRest(rightExp);

            expr = new SQLBinaryOpExpr(expr, SQLBinaryOperator.NotEqual, rightExp, getDbType());
        } else if (lexer.token() == Token.BANG) {
            lexer.nextToken();
            accept(Token.EQ);
            rightExp = shift();

            rightExp = equalityRest(rightExp);

            expr = new SQLBinaryOpExpr(expr, SQLBinaryOperator.NotEqual, rightExp, getDbType());
        }

        return expr;
    }
    
    public OraclePrimaryKey parsePrimaryKey() {
        lexer.nextToken();
        accept(Token.KEY);

        OraclePrimaryKey primaryKey = new OraclePrimaryKey();
        accept(Token.LPAREN);
        exprList(primaryKey.getColumns(), primaryKey);
        accept(Token.RPAREN);

        
        if (lexer.token() == Token.USING) {
            OracleUsingIndexClause using = parseUsingIndex();
            primaryKey.setUsing(using);
        }

        for (;;) {
            if (lexer.token() == Token.ENABLE) {
                lexer.nextToken();
                primaryKey.setEnable(Boolean.TRUE);
            } else if (lexer.token() == Token.DISABLE) {
                lexer.nextToken();
                primaryKey.setEnable(Boolean.FALSE);
            } else if (identifierEquals("VALIDATE")) {
                lexer.nextToken();
                primaryKey.setValidate(Boolean.TRUE);
            } else if (identifierEquals("NOVALIDATE")) {
                lexer.nextToken();
                primaryKey.setValidate(Boolean.FALSE);
            } else if (identifierEquals("RELY")) {
                lexer.nextToken();
                primaryKey.setRely(Boolean.TRUE);
            } else if (identifierEquals("NORELY")) {
                lexer.nextToken();
                primaryKey.setRely(Boolean.FALSE);
            } else {
                break;
            }
        }
        
        return primaryKey;
    }

    private OracleUsingIndexClause parseUsingIndex() {
        accept(Token.USING);
        accept(Token.INDEX);

        OracleUsingIndexClause using = new OracleUsingIndexClause();
        
        for (;;) {
            this.parseSegmentAttributes(using);

            if (lexer.token() == Token.COMPUTE) {
                lexer.nextToken();
                acceptIdentifier("STATISTICS");
                using.setComputeStatistics(true);
               continue;
            } else if (lexer.token() == Token.ENABLE) {
                lexer.nextToken();
                using.setEnable(true);
                continue;
            } else if (identifierEquals("REVERSE")) {
                lexer.nextToken();
                using.setReverse(true);
                continue;
            } else if (lexer.token() == Token.DISABLE) {
                lexer.nextToken();
                using.setEnable(false);
                continue;
            } else if (identifierEquals("LOCAL")) {
                lexer.nextToken();
                accept(Token.LPAREN);

                // http://docs.oracle.com/cd/B19306_01/server.102/b14200/statements_5010.htm#i2125897
                for (;;) {
                    SQLPartition partition = this.parsePartition();
                    partition.setParent(using);
                    using.getLocalPartitionIndex().add(partition);

                    if (lexer.token() == Token.COMMA) {
                        lexer.nextToken();
                        continue;
                    } else if (lexer.token() == Token.RPAREN) {
                        break;
                    } else {
                        throw new ParserException("TODO " + lexer.info());
                    }
                }
                accept(Token.RPAREN);
                continue;
            } else if (lexer.token() == Token.IDENTIFIER) {
                using.setTablespace(this.name());
                break;
            } else {
                break;
            }
        }
        return using;
    }
    
    public SQLColumnDefinition parseColumnRest(SQLColumnDefinition column) {
        column = super.parseColumnRest(column);

        for (;;) {
            if (lexer.token() == Token.ENABLE) {
                lexer.nextToken();
                column.setEnable(Boolean.TRUE);
            } else if (lexer.token() == Token.DISABLE) {
                lexer.nextToken();
                column.setEnable(Boolean.FALSE);
            } else if (identifierEquals("VALIDATE")) {
                lexer.nextToken();
                column.setValidate(Boolean.TRUE);
            } else if (identifierEquals("NOVALIDATE")) {
                lexer.nextToken();
                column.setValidate(Boolean.FALSE);
            } else if (identifierEquals("RELY")) {
                lexer.nextToken();
                column.setRely(Boolean.TRUE);
            } else if (identifierEquals("NORELY")) {
                lexer.nextToken();
                column.setRely(Boolean.FALSE);
            } else {
                break;
            }
        }
        
        return column;
    }
    
    public SQLExpr exprRest(SQLExpr expr) {
        expr = super.exprRest(expr);
        
        if (lexer.token() == Token.COLONEQ) {
            lexer.nextToken();
            SQLExpr right = expr();
            expr = new SQLBinaryOpExpr(expr, SQLBinaryOperator.Assignment, right, getDbType());
        }
        
        return expr;
    }
    
    public OracleLobStorageClause parseLobStorage() {
        lexer.nextToken();
        
        OracleLobStorageClause clause = new OracleLobStorageClause();
        
        accept(Token.LPAREN);
        this.names(clause.getItems());
        accept(Token.RPAREN);
        
        accept(Token.STORE);
        accept(Token.AS);
        
        for (;;) {
            if (identifierEquals("SECUREFILE")) {
                lexer.nextToken();
                clause.setSecureFile(true);
                continue;
            }
            
            if (identifierEquals("BASICFILE")) {
                lexer.nextToken();
                clause.setBasicFile(true);
                continue;
            }
            
            if (lexer.token() == Token.LPAREN) {
                lexer.nextToken();
                
                for (;;) {
                    this.parseSegmentAttributes(clause);
                    
                    if (lexer.token() == Token.ENABLE) {
                        lexer.nextToken();
                        accept(Token.STORAGE);
                        accept(Token.IN);
                        accept(Token.ROW);
                        clause.setEnable(true);
                        continue;
                    } else if (lexer.token() == Token.DISABLE) {
                        lexer.nextToken();
                        accept(Token.STORAGE);
                        accept(Token.IN);
                        accept(Token.ROW);
                        clause.setEnable(false);
                        continue;
                    }
                    
                    if (lexer.token() == Token.CHUNK) {
                        lexer.nextToken();
                        clause.setChunk(this.primary());
                        continue;
                    }
                    
                    if (lexer.token() == Token.NOCACHE) {
                        lexer.nextToken();
                        clause.setCache(false);
                        if (lexer.token() == Token.LOGGING) {
                            lexer.nextToken();
                            clause.setLogging(true);
                        }
                        continue;
                    }

                    if (lexer.token() == Token.KEEP_DUPLICATES) {
                        lexer.nextToken();
                        clause.setKeepDuplicate(true);
                        continue;
                    }

                    if (identifierEquals("PCTVERSION")) {
                        lexer.nextToken();
                        clause.setPctversion(this.expr());
                        continue;
                    }

                    if (identifierEquals("RETENTION")) {
                        lexer.nextToken();
                        clause.setRetention(true);
                        continue;
                    }

                    if (lexer.token() == Token.STORAGE) {
                        OracleStorageClause storageClause = this.parseStorage();
                        clause.setStorageClause(storageClause);
                        continue;
                    }
                    
                    break;
                }
                
                accept(Token.RPAREN);
            }
            
            break;
        }
        return clause;
    }
    
    public OracleStorageClause parseStorage() {
        lexer.nextToken();
        accept(Token.LPAREN);

        OracleStorageClause storage = new OracleStorageClause();
        for (;;) {
            if (identifierEquals("INITIAL")) {
                lexer.nextToken();
                storage.setInitial(this.expr());
                continue;
            } else if (lexer.token() == Token.NEXT) {
                lexer.nextToken();
                storage.setNext(this.expr());
                continue;
            } else if (lexer.token() == Token.MINEXTENTS) {
                lexer.nextToken();
                storage.setMinExtents(this.expr());
                continue;
            } else if (lexer.token() == Token.MAXEXTENTS) {
                lexer.nextToken();
                storage.setMaxExtents(this.expr());
                continue;
            } else if (lexer.token() == Token.MAXSIZE) {
                lexer.nextToken();
                storage.setMaxSize(this.expr());
                continue;
            } else if (lexer.token() == Token.PCTINCREASE) {
                lexer.nextToken();
                storage.setPctIncrease(this.expr());
                continue;
            } else if (identifierEquals("FREELISTS")) {
                lexer.nextToken();
                storage.setFreeLists(this.expr());
                continue;
            } else if (identifierEquals("FREELIST")) {
                lexer.nextToken();
                acceptIdentifier("GROUPS");
                storage.setFreeListGroups(this.expr());
                continue;
            } else if (identifierEquals("BUFFER_POOL")) {
                lexer.nextToken();
                storage.setBufferPool(this.expr());
                continue;
            } else if (identifierEquals("OBJNO")) {
                lexer.nextToken();
                storage.setObjno(this.expr());
                continue;
            } else if (lexer.token() == Token.FLASH_CACHE) {
                lexer.nextToken();
                FlashCacheType flashCacheType;
                if (identifierEquals("KEEP")) {
                    flashCacheType = FlashCacheType.KEEP;
                    lexer.nextToken();
                } else if (lexer.token() == Token.NONE) {
                    flashCacheType = FlashCacheType.NONE;
                    lexer.nextToken();
                } else {
                    accept(Token.DEFAULT);
                    flashCacheType = FlashCacheType.DEFAULT;
                }
                storage.setFlashCache(flashCacheType);
                continue;
            } else if (lexer.token() == Token.CELL_FLASH_CACHE) {
                lexer.nextToken();
                FlashCacheType flashCacheType;
                if (identifierEquals("KEEP")) {
                    flashCacheType = FlashCacheType.KEEP;
                    lexer.nextToken();
                } else if (lexer.token() == Token.NONE) {
                    flashCacheType = FlashCacheType.NONE;
                    lexer.nextToken();
                } else {
                    accept(Token.DEFAULT);
                    flashCacheType = FlashCacheType.DEFAULT;
                }
                storage.setCellFlashCache(flashCacheType);
                continue;
            }

            break;
        }
        accept(Token.RPAREN);
        return storage;
    }
    
    public SQLUnique parseUnique() {
        accept(Token.UNIQUE);

        OracleUnique unique = new OracleUnique();
        accept(Token.LPAREN);
        exprList(unique.getColumns(), unique);
        accept(Token.RPAREN);
        
        if (lexer.token() == Token.USING) {
            OracleUsingIndexClause using = parseUsingIndex();
            unique.setUsing(using);
        }

        return unique;
    }
    
    public OracleConstraint parseConstaint() {
        OracleConstraint constraint = (OracleConstraint) super.parseConstaint();
        
        for (;;) {
            if (lexer.token() == Token.EXCEPTIONS) {
                lexer.nextToken();
                accept(Token.INTO);
                SQLName exceptionsInto = this.name();
                constraint.setExceptionsInto(exceptionsInto);
                continue;
            }
            
            if (lexer.token() == Token.DISABLE) {
                lexer.nextToken();
                constraint.setEnable(false);
                continue;
            }
            
            if (lexer.token() == Token.ENABLE) {
                lexer.nextToken();
                constraint.setEnable(true);
                continue;
            }

            if (identifierEquals("VALIDATE")) {
                lexer.nextToken();
                constraint.setValidate(Boolean.TRUE);
                continue;
            }
            if (identifierEquals("NOVALIDATE")) {
                lexer.nextToken();
                constraint.setValidate(Boolean.FALSE);
                continue;
            }

            if (lexer.token() == Token.INITIALLY) {
                lexer.nextToken();
                
                if (lexer.token() == Token.IMMEDIATE) {
                    lexer.nextToken();
                    constraint.setInitially(Initially.IMMEDIATE);
                } else {
                    accept(Token.DEFERRED);
                    constraint.setInitially(Initially.DEFERRED);
                }
                
                continue;
            }
            
            if (lexer.token() == Token.NOT) {
                lexer.nextToken();
                if (identifierEquals("DEFERRABLE")) {
                    lexer.nextToken();
                    constraint.setDeferrable(false);
                    continue;
                }
                throw new ParserException("TODO " + lexer.token());
            }
            
            if (identifierEquals("DEFERRABLE")) {
                lexer.nextToken();
                constraint.setDeferrable(true);
                continue;
            }
            
            break;
        }

        return constraint;
    }
    
    protected OracleForeignKey createForeignKey() {
        return new OracleForeignKey();
    }
    
    protected SQLCheck createCheck() {
        return new OracleCheck();
    }

    protected SQLPartition parsePartition() {
        accept(Token.PARTITION);
        SQLPartition partition = new SQLPartition();
        partition.setName(this.name());

        SQLPartitionValue values = this.parsePartitionValues();
        if (values != null) {
            partition.setValues(values);
        }

        if (lexer.token() == Token.LPAREN) {
            lexer.nextToken();

            for (;;) {
                SQLSubPartition subPartition = parseSubPartition();

                partition.addSubPartition(subPartition);

                if (lexer.token() == Token.COMMA) {
                    lexer.nextToken();
                    continue;
                }

                break;
            }

            accept(Token.RPAREN);
        } else if (identifierEquals("SUBPARTITIONS")) {
            lexer.nextToken();
            SQLExpr subPartitionsCount = this.primary();
            partition.setSubPartitionsCount(subPartitionsCount);
        }

        for (;;) {
            parseSegmentAttributes(partition);

            if (lexer.token() == Token.LOB) {
                OracleLobStorageClause lobStorage = this.parseLobStorage();
                partition.setLobStorage(lobStorage);
                continue;
            }

            if (lexer.token() == Token.SEGMENT || identifierEquals("SEGMENT")) {
                lexer.nextToken();
                accept(Token.CREATION);
                if (lexer.token() == Token.IMMEDIATE) {
                    lexer.nextToken();
                    partition.setSegmentCreationImmediate(true);
                } else if (lexer.token() == Token.DEFERRED) {
                    lexer.nextToken();
                    partition.setSegmentCreationDeferred(true);
                }
                continue;
            }
            break;
        }
        return partition;
    }

    protected SQLSubPartition parseSubPartition() {
        acceptIdentifier("SUBPARTITION");

        SQLSubPartition subPartition = new SQLSubPartition();
        SQLName name = this.name();
        subPartition.setName(name);

        SQLPartitionValue values = this.parsePartitionValues();
        if (values != null) {
            subPartition.setValues(values);
        }

        if (lexer.token() == Token.TABLESPACE) {
            lexer.nextToken();
            subPartition.setTableSpace(this.name());
        }

        return subPartition;
    }

    public void parseSegmentAttributes(OracleSegmentAttributes attributes) {
        for (;;) {
            if (lexer.token() == Token.TABLESPACE) {
                lexer.nextToken();
                attributes.setTablespace(this.name());
                continue;
            } else if (lexer.token() == Token.NOCOMPRESS || identifierEquals("NOCOMPRESS")) {
                lexer.nextToken();
                attributes.setCompress(Boolean.FALSE);
                continue;
            } else if (identifierEquals("COMPRESS")) {
                lexer.nextToken();
                attributes.setCompress(Boolean.TRUE);

                if (lexer.token() == Token.LITERAL_INT) {
                    int compressLevel = this.parseIntValue();
                    attributes.setCompressLevel(compressLevel);
                } else if (identifierEquals("BASIC")) {
                    lexer.nextToken();
                    // TODO COMPRESS BASIC
                } else if (lexer.token() == Token.FOR) {
                    lexer.nextToken();
                    if (identifierEquals("OLTP")) {
                        lexer.nextToken();
                        attributes.setCompressForOltp(true);
                    } else {
                        throw new ParserException("TODO : " + lexer.info());
                    }
                }
                continue;
            } else if (identifierEquals("NOCOMPRESS")) {
                lexer.nextToken();
                attributes.setCompress(Boolean.FALSE);
                continue;
            } else if (lexer.token() == Token.LOGGING || identifierEquals("LOGGING")) {
                lexer.nextToken();
                attributes.setLogging(Boolean.TRUE);
                continue;
            } else if (identifierEquals("NOLOGGING")) {
                lexer.nextToken();
                attributes.setLogging(Boolean.FALSE);
                continue;
            } else if (lexer.token() == Token.INITRANS) {
                lexer.nextToken();
                attributes.setInitrans(this.parseIntValue());
                continue;
            } else if (lexer.token() == Token.MAXTRANS) {
                lexer.nextToken();
                attributes.setMaxtrans(this.parseIntValue());
            } else if (lexer.token() == Token.PCTINCREASE) {
                lexer.nextToken();
                attributes.setPctincrease(this.parseIntValue());
                continue;
            } else if (lexer.token() == Token.PCTFREE) {
                lexer.nextToken();
                attributes.setPctfree(this.parseIntValue());
                continue;
            } else if (lexer.token() == Token.STORAGE || identifierEquals("STORAGE")) {
                OracleStorageClause storage = this.parseStorage();
                attributes.setStorage(storage);
                continue;
            } else if (identifierEquals("PCTUSED")) {
                lexer.nextToken();
                attributes.setPctused(this.parseIntValue());
                continue;
            } else {
                break;
            }
        }
    }
}
