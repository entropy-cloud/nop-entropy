package io.nop.pdf.extract.processor;



public class StringProcessor {
	static final String CN_NUMS = "０１２３４５６７８９．／％—＋";
	static final String EN_NUMS = "0123456789./%-+";
	
	final StringBuilder sb;
	int pos = 0;
	
	public StringProcessor(String str){
		sb = new StringBuilder(str);
	}

	
	public String toString(){
		return sb.toString();
	}
	
	public int pos(){
		return pos;
	}
	
	public boolean startsWith(String str){
		int n = sb.length();
		if(pos + str.length() > n)
			return false;
		return sb.substring(pos, pos+str.length()).equals(str);
	}
	
	public String substring(int start, int end){
		return sb.substring(start, end);
	}
	
	public StringProcessor move(int n){
		pos += n;
		if(pos > sb.length())
			pos = sb.length();
		return this;
	}
	
	public int find(String str){
		return sb.indexOf(str, pos);
	}
	
	/**
	 * 所谓空白字符包含\r\n\t和中英文空格等
	 * 
	 * @return
	 */
	public StringProcessor removeWhitespace(){
		for(int i=0,n=sb.length();i<n;i++){
			char c = sb.charAt(i);
			if(Character.isWhitespace(c)){
				sb.deleteCharAt(i);
				i--;
				n--;
			}
		}
		return this;
	}
	
	public StringProcessor removeCrlf(){
		for(int i=0,n=sb.length();i<n;i++){
			char c = sb.charAt(i);
			if(c == '\r' || c == '\n'){
				sb.deleteCharAt(i);
				i--;
				n--;
			}
		}
		return this;
	}
	
	/**
	 * 将全角数字替换为半角英文字符,包括%+-等算符
	 * @return
	 */
	public StringProcessor normalizeDigit(){
		for(int i=0,n=sb.length();i<n;i++){
			char c = sb.charAt(i);
			int idx = CN_NUMS.indexOf(c);
			if(idx >= 0){
				sb.setCharAt(i, EN_NUMS.charAt(idx));
			}
		}
		return this;
	}
	
	public String searchDigits(){
		for(int i=pos,n=sb.length();i<n;i++){
			char c = sb.charAt(i);
			if(c >= '0' && c <= '9'){
				int j;
				for(j=i+1;j<n;j++){
					char c2 = sb.charAt(j);
					if(c2 < '0' || c2 > '9')
						break;
				}
				pos = j;
				return sb.substring(i,j);
			}
		}
		return null;
	}
	
	public StringProcessor replaceInteger(String symbol){
		for(int i=0,n=sb.length();i<n;i++){
			char c = sb.charAt(i);
			if(c >= '0' && c <= '9'){
				int j;
				for(j=i+1;j<n;j++){
					char c2 = sb.charAt(j);
					if(c2 < '0' || c2 > '9')
						break;
				}
				String s = symbol;
				if(j == i + 4 && sb.charAt(i) == '2' && (sb.charAt(i+1) <= '1')){
					// year
					s = "[Y"+ symbol+"]";
				}
				sb.replace(i, j, s);
				int d = s.length() - (j - i);
				n += d;
				i += s.length()-1;
			}
		}
		return this;
	}
}