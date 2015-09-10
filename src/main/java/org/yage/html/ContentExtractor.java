
package org.yage.html;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.cyberneko.html.parsers.DOMParser;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * class for extracting main text content from a web page
 * getting rid of ad-words, navigation-links, etc.
 * is not thread-safe
 * @author beethoven99@126.com
 * @since 0.1
 */
public class ContentExtractor{
	
    /**
     * HTML code of the page for extracting
     */
    private String htmlSourceCode;
    
    /**
     * result output destination
     */
    private Writer outputWriter;
    
    /**
     * whether consider <title> element content when extracting
     */
    private boolean useTitleTag;
    
    /**
     * only paragraphs longer than miniLength characters will be considered main content
     */
    private int miniLength;
    
    /**
     * the ratio of non-link text length, bigger than this value should not be 
     * seem as main text
     */
    private float miniPercent;
    
    /**
     * HTML parser
     */
    private DOMParser parser;
    
    private static String strTwenty="12345678901234567890";
    
    private boolean outputComment;
    
    private BufferedWriter bufferedWriter;
    
    public ContentExtractor(){
        this.parser=new DOMParser();
        this.useTitleTag=false;
        this.outputComment=false;
        this.miniLength=7;
        this.miniPercent=0.25f;
    }
    
    /**
     * do extracting task, using the specified html code as input
     * @param htmlcode
     * @return result
     */
    public String doExtracting(String htmlcode){
    	StringWriter stringWriter=new StringWriter();
    	this.bufferedWriter=new BufferedWriter(stringWriter);
    	this.htmlSourceCode=htmlcode;
    	this.doExtracting();
    	return stringWriter.getBuffer().toString();
    }
    
    /**
     * do extracting task
     * @param htmlcode
     * @param writer
     */
    public void doExtracting(String htmlcode, Writer writer){
    	this.bufferedWriter=new BufferedWriter(writer);
    	this.htmlSourceCode=htmlcode;
    	this.doExtracting();
    }
    
    /**
     * do extracting task
     */
    public void doExtracting(){
        this.parser.reset();
        try{
            this.parser.parse(new InputSource(new StringReader(htmlSourceCode)));
            Document d=parser.getDocument();
            NodeList nodes=d.getChildNodes();
            int len=nodes.getLength();
            for(int i=0;i<len;i++){
                if(isStringEmpty(nodes.item(i).getTextContent())){
                    //empty content, ignore it
                }else{
                    processNodes(nodes.item(i));
                }
            }
        }catch(SAXException ex){
            Logger.getLogger(ContentExtractor.class.getName()).log(Level.SEVERE, null, ex);
        }catch (IOException ex){
            Logger.getLogger(ContentExtractor.class.getName()).log(Level.SEVERE, null, ex);
        }finally{
            try {
                this.bufferedWriter.flush();
                this.bufferedWriter.close();
            } catch (IOException ex){
                Logger.getLogger(ContentExtractor.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return;
    }
    
    private void processNodes(Node node) throws IOException{
        NodeList nodes=node.getChildNodes();
        String nodename=node.getNodeName();
        boolean temp=false;
        temp|=nodename.equalsIgnoreCase("script");
        temp|=nodename.equalsIgnoreCase("#comment");
        temp|=nodename.equalsIgnoreCase("style");
        temp|=nodename.equalsIgnoreCase("a");
        temp|=nodename.equalsIgnoreCase("noScript");
        //added by voyage, 2015-9-6 15:57:37
        temp|=nodename.equalsIgnoreCase("iframe");
        if(!this.useTitleTag){
            //where consider title tag
            temp|=nodename.equalsIgnoreCase("head");
        }
        if(temp){
            return;
        }else if(hasTextChild(node)>0){
        	//evaluate it when containing non-empty child text node
            evaluateIt(node);
            return;
        }
        int len=nodes.getLength();
        for(int i = 0;i<len;i++){
            if(nodes.item(i).getTextContent().length()>0){
            	//if this child node has non-empty content, then process it
                processNodes(nodes.item(i));
            }
        }
    }
    
    /**
     * calculate the count of its empty-text child node
     * @param x node to calculate
     * @return
     */
    private static int hasTextChild(Node x){
        NodeList nodes=x.getChildNodes();
        int len=0;
        for(int i=0;i<nodes.getLength();i++){
            if(nodes.item(i).getNodeType()==Node.TEXT_NODE){
                if(nodes.item(i).getTextContent().trim().length()>0){
                    len++;
                }
            }
        }
        return len;
    }
    
    /**
     * looks like a main-text paragraph, then look further into it
     * @param node
     * @throws IOException
     */
    private void evaluateIt(Node node) throws IOException{
        if(node.getNodeName().trim().equalsIgnoreCase("A")){
            return;
        }else{
            String content=node.getTextContent().trim();
            float res=0;
            if(content.length()<this.miniLength){
                return;
            }
            //new calculation method
            StringPair mp=getStatus(node);
            res=mp.strHref.length();
            res/=(mp.strOther.length());
            if(res<this.miniPercent){
                if(finalProcess(mp.strOther).trim().length()>this.getMiniLength()){
                    //yes, this is what we want
                    if(this.outputComment){
                        this.bufferedWriter.write(mp.strOther+"["+mp+"="+res+"\r\n]");
                    }else{
                        this.bufferedWriter.write(mp.strOther+"\r\n");
                    }
                }
            }
        }
    }
    
    /**
     * 此函数查看这个结点里的内容中的“链接文本和其他文本的比例” 
     * @param node
     * @return
     */
    public static StringPair getStatus(Node node){
        StringPair res=new StringPair();
        StringPair temp;
        Node child=node.getFirstChild();
        while(child!=null){
            if(child.getNodeName().equalsIgnoreCase("A")){
                String x=child.getTextContent().trim();
                x=((x == null ? "" == null : x.equals(""))?strTwenty:x);//既然有链接那不太可能是空的，只是不能用文字表示罢了，来20
                res.strHref+=x;
                res.strOther+=x;
            }else if(child.getNodeName().equalsIgnoreCase("script")){
                //内容不计
            }else if(child.getNodeName().equalsIgnoreCase("#comment")){
                //内容不计
            }else if(child.getNodeName().equalsIgnoreCase("style")){
                //内容不计
            }else if(child.getChildNodes().getLength()>0){
                temp=getStatus(child);
                res.strHref+=temp.strHref;
                res.strOther+=temp.strOther;
            }else{
                res.strOther+=child.getTextContent().trim();
            }
            child=child.getNextSibling();
        }
        return res;
    }
    
    /**
     * utility method, test if string is empty
     * @param s String to test
     * @return true or false
     */
    public static boolean isStringEmpty(String s){
    	if(s==null){
    		return true;
    	}
    	if(s.trim().length()<1){
    		return true;
    	}
    	return false;
    }
    
    public static String finalProcess(String str){
        str=str.replaceAll("[\\[\\]\\d\\|,#$%^&*()_+-=:!~;.'\"：；“‘｜、。》《]","");
        String k=str.replaceAll("[\\w*]","");
        if(k.length()!=0){
            //既然它含有中文，那索性把所有的英文全部去掉
            return k;
        }
        return str;
    }
    
    public boolean isUseTitleTag(){
        return useTitleTag;
    }
    
    public void setUseTitleTag(boolean useTitleTag){
        this.useTitleTag = useTitleTag;
    }
    
    public int getMiniLength() {
        return miniLength;
    }
    
    public void setMiniLength(int miniLength){
        this.miniLength=miniLength;
    }
    
    public float getMiniPercent(){
        return miniPercent;
    }
    
    public void setMiniPercent(float miniPercent){
        this.miniPercent=miniPercent;
    }
    
    public void setOutpuComment(boolean b){
        this.outputComment=b;
    }
    
	public String getHtmlSourceCode() {
		return htmlSourceCode;
	}
	
	public void setHtmlSourceCode(String htmlSourceCode) {
		this.htmlSourceCode = htmlSourceCode;
	}

	public Writer getOutputWriter() {
		return outputWriter;
	}

	public void setOutputWriter(Writer outputWriter) {
		this.outputWriter = outputWriter;
	}
    
}