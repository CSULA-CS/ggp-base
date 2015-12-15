package org.ggp.base.tournament;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ReplayBuilder {

    public ReplayBuilder() {

    }

    private String tranformNode(String xml, String xsl) throws TransformerException {
        /*
        Transform a single game state to html
         */
        TransformerFactory factory = TransformerFactory.newInstance();
        StreamSource xslStream = new StreamSource(new StringReader(xsl));

        Transformer transformer = factory.newTransformer(xslStream);
        StreamSource in = new StreamSource(new StringReader(xml));
        // StreamResult out = new StreamResult(outputHTML);
        Writer writer = new StringWriter();
        StreamResult out = new StreamResult(writer);
        transformer.transform(in, out);
        // System.out.println("The generated HTML file is:" + writer.toString());
        return writer.toString();
    }

    /*
     * Return a list of game states in html representation
     */
    public List getReplayList(String xml, String xsl) throws TransformerException, XPathExpressionException, IOException, SAXException, ParserConfigurationException {
        InputSource source = new InputSource(new StringReader(xml));
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document document = db.parse(source);

        // Find '/state' in xml
        XPathFactory xpathFactory = XPathFactory.newInstance();
        XPath xpath = xpathFactory.newXPath();
        XPathExpression expr = xpath.compile("/match/herstory/state");
        NodeList nl = (NodeList) expr.evaluate(document, XPathConstants.NODESET);

        // For all '/state', transform each one to html
        List replayList = new ArrayList<String>();


        for (int i = 0; i < nl.getLength(); i++) {
            StringWriter writer = new StringWriter();
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.transform(new DOMSource(nl.item(i)), new StreamResult(writer));
            String xmlNode = writer.toString();
            replayList.add(tranformNode(xmlNode, xsl));
        }

        return replayList;
    }

    public static void main(String[] args) {
        /*try {
            System.out.println(getReplayList(xml, xsl));
        } catch (Exception e) {
            e.printStackTrace();
        }*/
    }
}
