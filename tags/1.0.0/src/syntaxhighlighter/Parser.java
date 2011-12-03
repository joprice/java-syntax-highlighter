/**
 * This is part of the Java SyntaxHighlighter.
 * 
 * It is distributed under MIT license. See the file 'readme.txt' for
 * information on usage and redistribution of this file, and for a
 * DISCLAIMER OF ALL WARRANTIES.
 * 
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
package syntaxhighlighter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.text.Segment;
import syntaxhighlighter.Brush.RegExpRule;

/**
 * The parser of the syntax highlighter.
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public class Parser {

    protected final List<Brush> htmlScriptBrushList;

    /**
     * Constructor.
     */
    public Parser() {
        htmlScriptBrushList = new ArrayList<Brush>();
    }

    /**
     * Add matched result to <code>matches</code>.
     * @param matches the list of matches
     * @param match the matched result
     */
    protected void addMatch(Map<Integer, List<MatchResult>> matches, MatchResult match) {
        if (matches == null || match == null) {
            return;
        }
        List<MatchResult> matchList = matches.get(match.getOffset());
        if (matchList == null) {
            matchList = new ArrayList<MatchResult>();
            matches.put(match.getOffset(), matchList);
        }
        matchList.add(match);
    }

    /**
     * Remove those matches that fufil the condition from <code>matches</code>.
     * @param matches the list of matches
     * @param start the start position in the document
     * @param end the end position in the document
     */
    protected void removeMatches(Map<Integer, List<MatchResult>> matches, int start, int end) {
        if (matches == null) {
            return;
        }
        for (int offset : matches.keySet()) {
            List<MatchResult> offsetMatches = matches.get(offset);

            ListIterator<MatchResult> iterator = offsetMatches.listIterator();
            while (iterator.hasNext()) {
                MatchResult match = iterator.next();

                // the start and end position in the document for this matched result
                int _start = match.getOffset(), _end = _start + match.getLength();

                if (_start >= end || _end <= start) {
                    // out of the range
                    continue;
                }
                if (_start >= start && _end <= end) {
                    // fit or within range
                    iterator.remove();
                } else if (_end <= end) {
                    // overlap with the start
                    // remove the style within the range and remain those without the range
                    iterator.set(new MatchResult(_start, start - _start, match.getStyleKey(), match.isBold()));
                } else if (_start >= start) {
                    // overlap with the end
                    // remove the style within the range and remain those without the range
                    iterator.set(new MatchResult(end, _end - end, match.getStyleKey(), match.isBold()));
                }
            }
        }
    }

    /**
     * Digest the <code>matches</code> and return a map with the style key be the key. 
     * @param matches the list of matches
     * @return the map with the style key be the key
     */
    protected Map<String, List<MatchResult>> getStyle(Map<Integer, List<MatchResult>> matches) {
        if (matches == null) {
            return null;
        }
        Map<String, List<MatchResult>> returnMap = new HashMap<String, List<MatchResult>>();

        // start from every loop, it will compare the offset from this record, if this record is greater than the offset, it will skip that match
        int offsetRecord = 0;

        for (int offset : matches.keySet()) {
            if (offsetRecord > offset) {
                continue;
            }

            List<MatchResult> offsetMatches = matches.get(offset);

            if (offsetMatches.isEmpty()) {
                continue;
            }

            // get only the match from this list with maximum length and ignore all others
            int maxLength = -1;
            MatchResult matchWithMaximumLength = null;
            for (MatchResult match : offsetMatches) {
                if (match.getLength() > maxLength) {
                    maxLength = match.getLength();
                    matchWithMaximumLength = match;
                }
            }

            // update the record for next loop
            offsetRecord = matchWithMaximumLength.getOffset() + matchWithMaximumLength.getLength();

            // add the match to returnMap
            List<MatchResult> styleList = returnMap.get(matchWithMaximumLength.getStyleKey());
            if (styleList == null) {
                styleList = new ArrayList<MatchResult>();
                returnMap.put(matchWithMaximumLength.getStyleKey(), styleList);
            }
            styleList.add(matchWithMaximumLength);
        }

        return returnMap;
    }

    /**
     * Parse the content start from <code>offset</code> with <code>length</code> and return the result.
     * @param brush the brush to use
     * @param htmlScript turn HTML-Script on or not
     * @param content the content to parse in char array
     * @param offset the offset
     * @param length the length
     * @return the parsed result, the key of the map is style key
     */
    public Map<String, List<MatchResult>> parse(Brush brush, boolean htmlScript, char[] content, int offset, int length) {
        if (brush == null || content == null) {
            return null;
        }
        Map<Integer, List<MatchResult>> matches = new TreeMap<Integer, List<MatchResult>>();
        return parse(matches, brush, htmlScript, content, offset, length);
    }

    /**
     * Parse the content start from <code>offset</code> with <code>length</code> with the brush and return the result.
     * All new matches will be added to <code>matches</code>.
     * @param matches the list of matches
     * @param brush the brush to use
     * @param htmlScript turn HTML-Script on or not
     * @param content the content to parse in char array
     * @param offset the offset
     * @param length the length
     * @return the parsed result, the key of the map is style key
     */
    protected Map<String, List<MatchResult>> parse(Map<Integer, List<MatchResult>> matches, Brush brush, boolean htmlScript, char[] content, int offset, int length) {
        if (matches == null || brush == null || content == null) {
            return null;
        }
        // parse the RegExpRule in the brush first
        List<RegExpRule> regExpRuleList = brush.getRegExpRuleList();
        for (RegExpRule regExpRule : regExpRuleList) {
            parse(matches, regExpRule, content, offset, length);
        }

        // parse the HTML-Script brushes later
        if (htmlScript) {
            synchronized (htmlScriptBrushList) {
                for (Brush htmlScriptBrush : htmlScriptBrushList) {
                    Pattern _pattern = htmlScriptBrush.getHTMLScriptRegExp().getpattern();

                    Matcher matcher = _pattern.matcher(new Segment(content, offset, length));
                    while (matcher.find()) {
                        // HTML-Script brush has superior priority, so remove all previous matches within the matched range
                        removeMatches(matches, matcher.start() + offset, matcher.end() + offset);

                        // the left tag of HTML-Script
                        int start = matcher.start(1) + offset, end = matcher.end(1) + offset;
                        addMatch(matches, new MatchResult(start, end - start, "script", false));

                        // the content of HTML-Script, parse it using the HTML-Script brush
                        start = matcher.start(2) + offset;
                        end = matcher.end(2) + offset;
                        parse(matches, htmlScriptBrush, false, content, start, end - start);

                        // the right tag of HTML-Script
                        start = matcher.start(3) + offset;
                        end = matcher.end(3) + offset;
                        addMatch(matches, new MatchResult(start, end - start, "script", false));
                    }
                }
            }
        }

        return getStyle(matches);
    }

    /**
     * Parse the content start from <code>offset</code> with <code>length</code> using the <code>regExpRule</code>.
     * All new matches will be added to <code>matches</code>.
     * @param matches the list of matches
     * @param regExpRule the RegExp rule to use
     * @param content the content to parse in char array
     * @param offset the offset
     * @param length the length
     */
    protected void parse(Map<Integer, List<MatchResult>> matches, RegExpRule regExpRule, char[] content, int offset, int length) {
        if (matches == null || regExpRule == null || content == null) {
            return;
        }
        Map<Integer, Object> groupOperations = regExpRule.getGroupOperations();

        Pattern regExpPattern = regExpRule.getPattern();
        Matcher matcher = regExpPattern.matcher(new Segment(content, offset, length));
        while (matcher.find()) {
            // deal with the matched result
            for (int groupId : groupOperations.keySet()) {
                Object operation = groupOperations.get(groupId);

                // the start and end position of the match
                int start = matcher.start(groupId), end = matcher.end(groupId);
                if (start == -1 || end == -1) {
                    continue;
                }
                start += offset;
                end += offset;

                if (operation instanceof String) {
                    // add the style to the match
                    addMatch(matches, new MatchResult(start, end - start, (String) operation, regExpRule.getBold()));
                } else {
                    // parse the result using the <code>operation</code> RegExpRule
                    parse(matches, (RegExpRule) operation, content, start, end - start);
                }
            }
        }
    }

    /**
     * Get the list of HTML Script brushes.
     * @return a copy of the list
     */
    public List<Brush> getHTMLScriptBrushList() {
        List<Brush> returnList;
        synchronized (htmlScriptBrushList) {
            returnList = new ArrayList<Brush>(htmlScriptBrushList);
        }
        return returnList;
    }

    /**
     * Set HTML Script brushes. Note that this will clear all previous recorded HTML Script brushes.
     * @param htmlScriptBrushList the list that contain the brushes
     */
    public void setHTMLScriptBrushList(List<Brush> htmlScriptBrushList) {
        synchronized (this.htmlScriptBrushList) {
            this.htmlScriptBrushList.clear();
            if (htmlScriptBrushList != null) {
                this.htmlScriptBrushList.addAll(htmlScriptBrushList);
            }
        }
    }

    /**
     * Add HTML Script brushes.
     * @param brush the brush to add
     */
    public void addHTMLScriptBrush(Brush brush) {
        if (brush == null) {
            return;
        }
        htmlScriptBrushList.add(brush);
    }

    /**
     * Matched result, it will be generated when parsing the content.
     */
    public static class MatchResult {

        /**
         * The position in the document for this matched result.
         */
        private int offset;
        /**
         * The length of the matched result.
         */
        private int length;
        /**
         * The style key for this matched result, see {@link syntaxhighlighter.Theme}.
         */
        private String styleKey;
        /**
         * Indicate whether this match should be bolded or not.
         * This will override the 'bold' setting of the style (by styleKey).
         * If it is null, there will be nothing done on the 'bold' of the style.
         */
        private Boolean bold;

        /**
         * Constructor.
         * @param offset the position in the document for this matched result
         * @param length the length of the matched result.
         * @param styleKey the style key for this matched result, cannot be null, see {@link syntaxhighlighter.Theme}
         * @param bold indicate whether this match should be bolded or not, for details see {@link #bold}
         */
        protected MatchResult(int offset, int length, String styleKey, Boolean bold) {
            if (styleKey == null) {
                throw new NullPointerException("argument 'styleKey' cannot be null");
            }
            this.offset = offset;
            this.length = length;
            this.styleKey = styleKey;
            this.bold = bold;
        }

        /**
         * The position in the document for this matched result.
         */
        public int getOffset() {
            return offset;
        }

        /**
         * The position in the document for this matched result.
         */
        public void setOffset(int offset) {
            this.offset = offset;
        }

        /**
         * The length of the matched result.
         */
        public int getLength() {
            return length;
        }

        /**
         * The length of the matched result.
         */
        public void setLength(int length) {
            this.length = length;
        }

        /**
         * The style key for this matched result, see {@link syntaxhighlighter.Theme}.
         */
        public String getStyleKey() {
            return styleKey;
        }

        /**
         * Indicate whether this match should be bolded or not.
         * This will override the 'bold' setting of the style (by styleKey).
         * If it is null, there will be nothing done on the 'bold' of the style.
         */
        public Boolean isBold() {
            return bold;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();

            sb.append("[");
            sb.append(offset);
            sb.append(", ");
            sb.append(length);
            sb.append(", ");
            sb.append(styleKey);
            sb.append(", ");
            sb.append(bold);
            sb.append("]");

            return sb.toString();
        }
    }
}
