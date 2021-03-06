package rnb.analyzer.nori.analyzer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ko.KoreanAnalyzer;
import org.apache.lucene.analysis.ko.KoreanPartOfSpeechStopFilter;
import org.apache.lucene.analysis.ko.KoreanTokenizer;
import org.apache.lucene.analysis.ko.KoreanTokenizer.DecompoundMode;
import org.apache.lucene.analysis.ko.POS;
import org.apache.lucene.analysis.ko.POS.Tag;
import org.apache.lucene.analysis.ko.dict.UserDictionary;
import org.apache.lucene.analysis.ko.tokenattributes.PartOfSpeechAttribute;
import org.apache.lucene.analysis.ko.tokenattributes.ReadingAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NoriAnalyzer class
 * -Nori 형태소 분석기를 이용하여 문자열을 입력받아 형태소분석이 완료된 토큰리스트 혹은
 *  하나의 스트링을 돌려주는 등의 형태소분석 유틸기능을 수행하는 클래스이다.
 * @author yun-yeoseong
 *
 */
public class NoriAnalyzer {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(NoriAnalyzer.class);
	
	/**
	 * KoreanAnalyzer의 속성값을 설정할 수 있는 enum 타입.
	 * @author yun-yeoseong
	 * BASIC - DecompoundMode.MIXED
	 * 
	 * 추후 요구사항에 근거하여 여러가지 모드를 지원한다.
	 *
	 */
	public enum AnalyzerMode {
		BASIC;
	}
	
	private static final Set<POS.Tag> BASIC_STOP_TAGS = Arrays.asList(
			  POS.Tag.E,
		      POS.Tag.IC,
		      POS.Tag.J,
//		      POS.Tag.MAG,
		      POS.Tag.MAJ,
		      POS.Tag.MM,
		      POS.Tag.SP,
		      POS.Tag.SSC,
		      POS.Tag.SSO,
//		      POS.Tag.SC,
		      POS.Tag.SE,
		      POS.Tag.XPN,
		      POS.Tag.XSA,
		      POS.Tag.XSN,
		      POS.Tag.XSV,
		      POS.Tag.UNA,
		      POS.Tag.NA,
		      POS.Tag.VSV			
			).stream().collect(Collectors.toSet());
	
	private static final String USER_DIC_PATH = "/userdict/userdict_ko.txt";
	
	private List<AnalyzingTerm> termsOfResult;
	
	//텀 속성확인
	private CharTermAttribute charAttr ;
	
	//위치증가값 속성 확인
	private PositionIncrementAttribute posAttr ;
	
	//오프셋 위치확인
	private OffsetAttribute offsetAttr ;
	
	//텀타입 속성 확인
	private TypeAttribute typeAttr ;
	
	//Part of Speech 속성확인
	private PartOfSpeechAttribute partAttr ;
	
	//Reading 속성확인
	private ReadingAttribute readingAttr ;
	
	private UserDictionary userDict;
	
	private DecompoundMode decompoundMode;
	
	private Set<POS.Tag> stopTags;
	
	private boolean outputUnknownUnigrams;
	
	/**
	 * Default Constructor. So all attributes is default.
	 */
	public NoriAnalyzer() {
		this(AnalyzerMode.BASIC,null);
	}
	
	/**
	 * BASIC - DecompoundMode.MIXED,BASIC_STOP_TAGS,outputUnknownUnigrams=false
	 * @param mode
	 */
	public NoriAnalyzer(AnalyzerMode mode,String userDictPath) {
		
		try {
			this.userDict = UserDictionary.open(new InputStreamReader(this.getClass().getResourceAsStream(USER_DIC_PATH)));
			if(mode.equals(AnalyzerMode.BASIC)) {
				this.decompoundMode = decompoundMode.MIXED;
				this.stopTags = BASIC_STOP_TAGS;
				this.outputUnknownUnigrams = false;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			LOGGER.error("can not open userDict, so not used user dictionary !");
			this.userDict = null;
			this.decompoundMode = decompoundMode.MIXED;
			this.stopTags = BASIC_STOP_TAGS;
			this.outputUnknownUnigrams = false;
		} 
		
	}
	
	/**
	 * Custom Analyzer setting. So all parameter required.
	 * 
	 * @param userDict
	 * @param decompoundMode
	 * @param stopTags
	 * @param outputUnknownUnigrams
	 */
	public NoriAnalyzer(UserDictionary userDict, DecompoundMode decompoundMode, Set<Tag> stopTags,
			boolean outputUnknownUnigrams) {
		
		this.userDict = userDict;
		this.decompoundMode = decompoundMode;
		this.stopTags = stopTags;
		this.outputUnknownUnigrams = outputUnknownUnigrams;
	}

	/**
	 * 형태소분석 결과로 하나의 연결된 문자열을 반환값으로 준다.
	 * @param text - 분석하길 원하는 문자열
	 * @return
	 * @throws IOException 
	 */
	public String analyzeForString(String text) {
		
		StringBuffer buffer = new StringBuffer();
		
		try(Analyzer analyzer = new KoreanAnalyzer(userDict, decompoundMode, stopTags, outputUnknownUnigrams);){
			
			displayTokens(analyzer, text);
			
			for(AnalyzingTerm term : termsOfResult) {
				
				buffer.append(term.getToken()+" ");
			
			}
			
			return buffer.toString().trim();
			
		}catch (Exception e) {
			// TODO: handle exception
			LOGGER.error("NoriAnalyzer.analyzeForString() :::: {}",e);
			
			return null;
		}
		
	}
	
	/**
	 * 형태소분석 결과로 분석된 텀을 리스트로 반환한다.
	 * @param text - 분석하길 원하는 문자열
	 * @return
	 * @throws IOException 
	 */
	public List<AnalyzingTerm> analyzeForTerms(String text) throws IOException{
		
		try(Analyzer analyzer = new KoreanAnalyzer(userDict, decompoundMode, stopTags, outputUnknownUnigrams);){
			
			displayTokens(analyzer, text);
			
			return termsOfResult;
			
		}catch (Exception e) {
			// TODO: handle exception
			LOGGER.error("NoriAnalyzer.analyzeForTerms() :::: {}",e);
			
			return null;
		}
	}
	
	
	private void displayTokens(Analyzer analyzer, String text) throws IOException{
		
		displayTokens(analyzer.tokenStream("result", new StringReader(text)));
		
	}
	
	/**
	 * TokenStream을 받아서 형태소분석된 토큰을 꺼낸다.
	 * @param stream
	 * @throws IOException
	 */
	private void displayTokens(TokenStream stream) throws IOException{
		
		charAttr = stream.addAttribute(CharTermAttribute.class);
		
		posAttr = stream.addAttribute(PositionIncrementAttribute.class);
		
		offsetAttr = stream.addAttribute(OffsetAttribute.class);
		
		typeAttr = stream.addAttribute(TypeAttribute.class);
		
		partAttr = stream.addAttribute(PartOfSpeechAttribute.class);
		
		readingAttr = stream.addAttribute(ReadingAttribute.class);
		
		stream.reset();
		
		int position = 0;
		
		AnalyzingTerm term = null;
		
		termsOfResult = new ArrayList<>();
		
		while(stream.incrementToken()) {
			
			int increment = posAttr.getPositionIncrement();
			
			position = position + increment;
			
			term = new AnalyzingTerm(charAttr.toString()
									,position
									,offsetAttr.startOffset()
									,offsetAttr.endOffset()
									,typeAttr.type()
									,partAttr.getPOSType().toString()
									,partAttr.getLeftPOS().toString()
									,partAttr.getRightPOS().toString()
									,readingAttr.getReading());
			
			termsOfResult.add(term);
		}
		
		stream.end();
		stream.close();
	}
	
}
