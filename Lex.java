//package hw1;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

public class Lex {
	
	enum State {
		A, B, C, D
	}
	
	enum TokenType {
		NUM, BINOP, INCROP, LVALUE, LPAREN, RPAREN, EOF
	}
	
	public static class Token {
		TokenType type;
		String value;
		int linenumber;
		
		Token(TokenType type, String value, int linenumber) {
			this.type = type;
			this.value = value;
			this.linenumber = linenumber;
		}
	}
	
	static char[] inputs = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '-', '(', ')', '#', '$', ' ', '\n'};
	
	// NFA Transitions
	static State[][] transitions = {
		/* 			0		 1		   2	   3		4	     5		  6		   7	    8		 9		  +		  -		    (		 )		  #		   $	   " "	    \n	  */
		/* A */ {State.A, State.A, State.A, State.A, State.A, State.A, State.A, State.A, State.A, State.A, State.B, State.C, State.A, State.A, State.D, State.A, State.A, State.A},
		/* B */ {State.A, State.A, State.A, State.A, State.A, State.A, State.A, State.A, State.A, State.A, State.A, State.C, State.A, State.A, State.D, State.A, State.A, State.A},
		/* C */ {State.A, State.A, State.A, State.A, State.A, State.A, State.A, State.A, State.A, State.A, State.B, State.A, State.A, State.A, State.D, State.A, State.A, State.A},
		/* D */ {State.D, State.D, State.D, State.D, State.D, State.D, State.D, State.D, State.D, State.D, State.D, State.D, State.D, State.D, State.D, State.D, State.D, State.A}
	};

	static State currentState = State.A;
	static int linenumber = 1;
	
	static HashMap<State, HashMap<Character, State>> transitionTable = new HashMap<State, HashMap<Character, State>>();
	
	static void init() {
		// Initialize the transition table (hash map) so we can access the row by the State and the column by the input
		for (int i = 0; i < State.values().length; i++) {
			HashMap<Character, State> row = new HashMap<Character, State>();
			for (int j = 0; j < inputs.length; j++) {
				row.put(inputs[j], transitions[i][j]);
			}
			
			transitionTable.put(State.values()[i], row);
		}
	}
	
	// Construct tokens from the input stream
	static ArrayList<Token> lex(InputStream in) throws Exception {
		ArrayList<Token> tokens = new ArrayList<Token>();
		
		int c;
		
		while ((c = in.read()) != -1) {
			
			// Replace all kinds of whitespaces with a single space
			if (Character.isWhitespace(c) && c != '\n')
				c = ' ';
			
			// Update line number
			if (c == '\n')
				linenumber++;
			
			if (currentState == State.A) {
				
				Token t = getTokenForChar((char) c);
				if (t != null)
					tokens.add(t);
				
			} else if (currentState == State.B) {
				
				if (c == '+') {
					tokens.add(new Token(TokenType.INCROP, "++", linenumber));
				} else {
					// The previous character must be a +
					tokens.add(new Token(TokenType.BINOP, "+", linenumber));
					
					Token t = getTokenForChar((char) c);
					if (t != null)
						tokens.add(t);
				}
				
			} else if (currentState == State.C) {
				
				if (c == '-') {
					tokens.add(new Token(TokenType.INCROP, "--", linenumber));
				} else {
					// The previous character must be a -
					tokens.add(new Token(TokenType.BINOP, "-", linenumber));
					
					Token t = getTokenForChar((char) c);
					if (t != null)
						tokens.add(t);
				}
				
			}
			
			// Get the next state from transition table given the current state and input
			State nextState = transitionTable.get(currentState).get((char) c);
			
			if (nextState != null) {
				// Valid transition
				currentState = nextState;
			} else if (currentState != State.D) {
				// Invalid transition => invalid input
				//System.out.printf("Invalid input: %c", c);
				throw new Exception();
			}
		}
		
		// We have reached EOF
		if (currentState == State.B)
			tokens.add(new Token(TokenType.BINOP, "+", linenumber));
		else if (currentState == State.C)
			tokens.add(new Token(TokenType.BINOP, "-", linenumber));
		
		// Create and add the EOF token to tokens
		tokens.add(new Token(TokenType.EOF, "EOF", linenumber));
		
		return tokens;
	}
	
	private static Token getTokenForChar(char c) {
		if (c >= '0' && c <= '9')
			return new Token(TokenType.NUM, Character.toString(c), linenumber);
		else if (c == '(')
			return new Token(TokenType.LPAREN, Character.toString(c), linenumber);
		else if (c == ')')
			return new Token(TokenType.RPAREN, Character.toString(c), linenumber);
		else if (c == '$')
			return new Token(TokenType.LVALUE, Character.toString(c), linenumber);
		return null;
	}
}
