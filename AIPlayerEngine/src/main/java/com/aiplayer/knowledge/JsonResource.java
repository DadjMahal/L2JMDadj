package com.aiplayer.knowledge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GK-6 — tiny DEPENDENCY-FREE JSON reader for the EXTRACTOR-GENERATED knowledge files.
 *
 * <p>The engine ships no JSON library (offline mvn gate), so this compact reader only needs to
 * understand the shapes the datapack extractors emit: a top-level JSON array of flat objects
 * with string / int / double / bool / null values and simple arrays of ints or strings
 * (plus one nested-object case for htmGraph, which KnowledgeBase deliberately skips). It throws
 * on any token it does not understand rather than silently mis-parsing the data.
 */
public final class JsonResource
{
    private JsonResource()
    {
    }

    public static List<Map<String, Object>> autoObjectList(String fileName)
    {
        String text = read(fileName);
        Parser p = new Parser(text);
        Object v = p.parseValue();
        p.skipWs();
        return (List<Map<String, Object>>) v;
    }

    private static String read(String fileName)
    {
        try (InputStream in = JsonResource.class.getClassLoader()
                .getResourceAsStream("knowledge/" + fileName))
        {
            if (in == null)
            {
                throw new IllegalStateException("knowledge/" + fileName + " not on classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new IllegalStateException("cannot read knowledge/" + fileName, e);
        }
    }

    /** Character-at-a-time JSON parser (values only). */
    static final class Parser
    {
        private final String s;
        private int i;

        Parser(String s)
        {
            this.s = s;
        }

        void skipWs()
        {
            while (i < s.length() && Character.isWhitespace(s.charAt(i)))
            {
                i++;
            }
        }

        Object parseValue()
        {
            skipWs();
            char c = s.charAt(i);
            switch (c)
            {
                case '{':
                    return parseObject();
                case '[':
                    return parseArray();
                case '"':
                    return parseString();
                case 't':
                    i += 4;
                    return Boolean.TRUE;
                case 'f':
                    i += 5;
                    return Boolean.FALSE;
                case 'n':
                    i += 4;
                    return null;
                default:
                    return parseNumber();
            }
        }

        private Map<String, Object> parseObject()
        {
            Map<String, Object> out = new LinkedHashMap<>();
            i++; // '{'
            skipWs();
            if (peek() == '}')
            {
                i++;
                return out;
            }
            while (true)
            {
                skipWs();
                String key = parseString();
                skipWs();
                expect(':');
                out.put(key, parseValue());
                skipWs();
                char c = s.charAt(i);
                i++;
                if (c == ',')
                {
                    continue;
                }
                if (c == '}')
                {
                    return out;
                }
                throw new IllegalStateException("expected , or } at " + i);
            }
        }

        private List<Object> parseArray()
        {
            List<Object> out = new ArrayList<>();
            i++; // '['
            skipWs();
            if (peek() == ']')
            {
                i++;
                return out;
            }
            while (true)
            {
                out.add(parseValue());
                skipWs();
                char c = s.charAt(i);
                i++;
                if (c == ',')
                {
                    continue;
                }
                if (c == ']')
                {
                    return out;
                }
                throw new IllegalStateException("expected , or ] at " + i);
            }
        }

        private String parseString()
        {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true)
            {
                char c = s.charAt(i++);
                if (c == '"')
                {
                    return sb.toString();
                }
                if (c == '\\')
                {
                    char e = s.charAt(i++);
                    switch (e)
                    {
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case '/': sb.append('/'); break;
                        default: sb.append(e); // \" \\ etc.
                    }
                }
                else
                {
                    sb.append(c);
                }
            }
        }

        private Number parseNumber()
        {
            int start = i;
            while (i < s.length() && "-.0123456789eE".indexOf(s.charAt(i)) >= 0)
            {
                i++;
            }
            String num = s.substring(start, i);
            if (num.indexOf('.') >= 0 || num.indexOf('e') >= 0 || num.indexOf('E') >= 0)
            {
                return Double.parseDouble(num);
            }
            return Integer.parseInt(num);
        }

        private char peek()
        {
            return s.charAt(i);
        }

        private char next()
        {
            return s.charAt(i++);
        }

        private void expect(char c)
        {
            if (s.charAt(i) != c)
            {
                throw new IllegalStateException("expected '" + c + "' at " + i + " in " + s.substring(0, Math.min(s.length(), i + 12)));
            }
            i++;
        }
    }
}
