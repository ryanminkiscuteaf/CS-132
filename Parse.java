//package hw1;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class Parse {
	static ArrayList<Lex.Token> tokens = new ArrayList<Lex.Token>();
	static int currentTokenIndex = 0;
	static Lex.Token currentToken = null;
	static boolean printFirstTime = true;
	
	static StringBuilder outStr = new StringBuilder();
	
	private static Lex.Token nextToken() {
		return tokens.get(currentTokenIndex++);
	}
	
	private static void printError() {
		System.out.printf("Parse error in line %d\n", currentToken.linenumber);
		System.exit(1);
	}
	
	private static void printFormattedToken(String s) {
		String delimiter = "";
		if (printFirstTime) {
			printFirstTime = false;
		} else {
			delimiter = " ";
		}
		
		//System.out.print(delimiter + s);
		outStr.append(delimiter);
		outStr.append(s);
	}
	
	public static void main(String[] args) throws IOException {
		Lex.init();
		
		try {
			InputStream in = System.in;
			tokens = Lex.lex(in);
			currentToken = nextToken();
			
			// Start the initial production
			E();
			
			// Print out output string
			System.out.println(outStr.toString());
			
			// Print out success message
			System.out.println("Expression parsed successfully");
		} catch (Exception e) {
			//System.out.println(e.getMessage());
			System.out.printf("Parse error in line %d\n", Lex.linenumber);
		}
	}
	
	/*
	 * Match current token with the expected token
	 * Print error message if one occurs
	 */

	private static void match(String tokenString) {
		if (currentToken.value.equals(tokenString)) {
			
			//
			//System.out.println("-> " + tokenString);
			//
			
			if (currentToken.type != Lex.TokenType.EOF)
				currentToken = nextToken();
	
		} else {
			printError();
		}
	}
	
	/*
	 * The methods below correspond to the nonterminals in the attached grammar
	 */
	
	private static void E() {
		if (currentToken.type == Lex.TokenType.NUM || currentToken.type == Lex.TokenType.INCROP 
				|| currentToken.type == Lex.TokenType.LPAREN || currentToken.type == Lex.TokenType.LVALUE) {
			E1();
			match("EOF");
			//System.out.println();
		}
		else {
			printError();
		}
	}
	
	private static void E1() {
		if (currentToken.type == Lex.TokenType.NUM || currentToken.type == Lex.TokenType.INCROP 
				|| currentToken.type == Lex.TokenType.LPAREN || currentToken.type == Lex.TokenType.LVALUE) {
			E2();
			E1_();
		}
		else {
			printError();
		}
	}
	
	private static void E1_() {
		if (currentToken.type == Lex.TokenType.NUM || currentToken.type == Lex.TokenType.INCROP || 
				currentToken.type == Lex.TokenType.LPAREN || currentToken.type == Lex.TokenType.LVALUE) {
			E2();
			
			// IMPORTANT: POSTFIX PRINT
			printFormattedToken("_");
			
			E1_();	
		} else if (currentToken.type == Lex.TokenType.RPAREN || currentToken.type == Lex.TokenType.EOF) {
			// nothing
		} else {
			printError();
		}
	}
	
	private static void E2() {
		if (currentToken.type == Lex.TokenType.NUM || currentToken.type == Lex.TokenType.INCROP || 
				currentToken.type == Lex.TokenType.LPAREN || currentToken.type == Lex.TokenType.LVALUE) {
			E3();
			E2_();
		} else {
			printError();
		}
	}
	
	private static void E2_() {
		if (currentToken.value.equals("+")) {
			match("+");
			E3();
			
			// IMPORTANT: POSTFIX PRINT
			printFormattedToken("+");
			
			E2_();
		} else if (currentToken.value.equals("-")) {
			match("-");
			E3();
			
			// IMPORTANT: POSTFIX PRINT
			printFormattedToken("-");
			
			E2_();
		}
	}
	
	private static void E3() {
		if (currentToken.type == Lex.TokenType.NUM || currentToken.type == Lex.TokenType.LPAREN || currentToken.type == Lex.TokenType.LVALUE) {
			E4();
		} else if (currentToken.value.equals("++")) {
			match("++");
			E3();
			
			// IMPORTANT: POSTFIX PRINT
			printFormattedToken("++_");
			
		} else if (currentToken.value.equals("--")) {
			match("--");			
			E3();
			
			// IMPORTANT: POSTFIX PRINT
			printFormattedToken("--_");
			
		} else {
			printError();
		}
	}
	
	private static void E4() {
		if (currentToken.type == Lex.TokenType.NUM || currentToken.type == Lex.TokenType.LPAREN || currentToken.type == Lex.TokenType.LVALUE) {
			E5();
			E4_();
		} else {
			printError();
		}
	}
	
	private static void E4_() {
		if (currentToken.value.equals("++")) {
			match("++");
			
			// IMPORTANT: POSTFIX PRINT
			printFormattedToken("_++");
			
			E4_();
			
		} else if (currentToken.value.equals("--")) {
			match("--");
			
			// IMPORTANT: POSTFIX PRINT
			printFormattedToken("_--");
						
			E4_();
		} 
	}
	
	private static void E5() {
		if (currentToken.type == Lex.TokenType.NUM || currentToken.type == Lex.TokenType.LPAREN) {
			E7();
		} else if (currentToken.type == Lex.TokenType.LVALUE) {
			match("$");
			E6();
			
			// IMPORTANT: POSTFIX PRINT
			printFormattedToken("$");
			
		} else {
			printError();
		}
	}
	
	private static void E6() {
		if (currentToken.value.equals("++")) {
			match("++");
			E6();
			
			// IMPORTANT: POSTFIX PRINT
			printFormattedToken("++_");
			
		} else if (currentToken.value.equals("--")) {
			match("--");
			E6();
			
			// IMPORTANT: POSTFIX PRINT
			printFormattedToken("--_");
			
		} else if (currentToken.type == Lex.TokenType.NUM || currentToken.type == Lex.TokenType.LPAREN || currentToken.type == Lex.TokenType.LVALUE) {
			E5();
		} else {
			printError();
		}
	}
	
	private static void E7() {
		if (currentToken.type == Lex.TokenType.NUM) {
			String numStr = currentToken.value;
			match(numStr);
			
			// IMPORTANT: POSTFIX PRINT
			printFormattedToken(numStr);
			
		} else if (currentToken.type == Lex.TokenType.LPAREN) {
			match("(");
			E1();
			match(")");
		} else {
			printError();
		}
	}
}
