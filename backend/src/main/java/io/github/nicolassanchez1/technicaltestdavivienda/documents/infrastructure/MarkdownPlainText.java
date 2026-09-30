package io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;

/**
 * Renders a parsed Markdown node as the plain text that gets indexed: what a reader would see with
 * the markup taken away.
 *
 * <p>It is written by hand rather than reusing commonmark's text renderer because that one prints a
 * link as its label followed by its destination, and a URL in a tsvector only adds lexemes nobody
 * searches for.
 */
final class MarkdownPlainText extends AbstractVisitor {

    private final StringBuilder text = new StringBuilder();
    private int listItemDepth;

    private MarkdownPlainText() {}

    static String of(Node node) {
        MarkdownPlainText plainText = new MarkdownPlainText();
        node.accept(plainText);
        return plainText.text.toString().strip();
    }

    @Override
    public void visit(Text node) {
        text.append(node.getLiteral());
    }

    @Override
    public void visit(Code node) {
        text.append(node.getLiteral());
    }

    @Override
    public void visit(FencedCodeBlock node) {
        appendBlock(node.getLiteral());
    }

    @Override
    public void visit(IndentedCodeBlock node) {
        appendBlock(node.getLiteral());
    }

    @Override
    public void visit(Paragraph node) {
        startBlock();
        visitChildren(node);
    }

    @Override
    public void visit(Heading node) {
        startBlock();
        visitChildren(node);
    }

    @Override
    public void visit(BlockQuote node) {
        startBlock();
        visitChildren(node);
    }

    @Override
    public void visit(ListItem node) {
        listItemDepth++;
        startLine();
        visitChildren(node);
        listItemDepth--;
    }

    /** The label is what the document says; the destination is addressing, not prose. */
    @Override
    public void visit(Link node) {
        visitChildren(node);
    }

    /** Same for an image: the alternative text is content, the file it points at is not. */
    @Override
    public void visit(Image node) {
        visitChildren(node);
    }

    @Override
    public void visit(HtmlInline node) {
        // Raw HTML is markup, and the viewer never renders it as HTML, so it is not content either.
    }

    @Override
    public void visit(HtmlBlock node) {
        // Same reasoning as inline HTML.
    }

    @Override
    public void visit(ThematicBreak node) {
        // A rule carries no text.
    }

    @Override
    public void visit(SoftLineBreak node) {
        startLine();
    }

    @Override
    public void visit(HardLineBreak node) {
        startLine();
    }

    private void appendBlock(String literal) {
        String content = literal == null ? "" : literal.strip();
        if (!content.isEmpty()) {
            startBlock();
            text.append(content);
        }
    }

    /** Items of one list read as consecutive lines; anything else is separated by a blank line. */
    private void startBlock() {
        if (text.isEmpty()) {
            return;
        }
        startLine();
        if (listItemDepth == 0 && text.length() >= 2 && text.charAt(text.length() - 2) != '\n') {
            text.append('\n');
        }
    }

    private void startLine() {
        if (!text.isEmpty() && text.charAt(text.length() - 1) != '\n') {
            text.append('\n');
        }
    }
}
