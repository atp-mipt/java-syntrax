package org.atpfivt.jsyntrax.generators;

import org.atpfivt.jsyntrax.generators.elements.*;
import org.atpfivt.jsyntrax.styles.NodeStyle;
import org.atpfivt.jsyntrax.styles.Style;
import org.atpfivt.jsyntrax.styles.TitlePosition;
import org.atpfivt.jsyntrax.units.Unit;
import org.atpfivt.jsyntrax.units.nodes.Bullet;
import org.atpfivt.jsyntrax.units.nodes.Node;
import org.atpfivt.jsyntrax.units.nodes.NoneNode;
import org.atpfivt.jsyntrax.units.tracks.Choice;
import org.atpfivt.jsyntrax.units.tracks.Line;
import org.atpfivt.jsyntrax.units.tracks.loop.Loop;
import org.atpfivt.jsyntrax.units.tracks.loop.Toploop;
import org.atpfivt.jsyntrax.units.tracks.opt.Opt;
import org.atpfivt.jsyntrax.units.tracks.opt.Optx;
import org.atpfivt.jsyntrax.units.tracks.stack.Indentstack;
import org.atpfivt.jsyntrax.units.tracks.stack.Rightstack;
import org.atpfivt.jsyntrax.units.tracks.stack.Stack;
import org.atpfivt.jsyntrax.util.Pair;
import org.atpfivt.jsyntrax.visitors.Visitor;

import java.awt.Color;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @brief class for building canvas by Unit
 */
public class SVGCanvasBuilder implements Visitor{
    private Map<String, String> urlMap;
    private Style style;
    private SVGCanvas canvas;

    public SVGCanvasBuilder() {
        this.style = new Style(1, false);
        this.urlMap = Collections.emptyMap();
    }

    public SVGCanvasBuilder withStyle(Style style) {
        this.style = style;
        return this;
    }

    public SVGCanvasBuilder withUrlMap(Map<String, String> urlMap) {
        this.urlMap = urlMap;
        return this;
    }

    public SVGCanvasBuilder withTitle(String title) {
        this.title = title;
        return this;
    }

    public SVGCanvas generateSVG(Unit root) {
        this.canvas = new SVGCanvas(this.style, urlMap);
        parseDiagram(new Line(
                new ArrayList<>(List.of(new Bullet(), root, new Bullet()))
        ), true);

        if (title == null) {
            return canvas;
        }

        String tag = canvas.getCanvasTag().orElse(null);
        Pair<Pair<Integer, Integer>, Pair<Integer, Integer>> bbox =
                canvas.getBoundingBoxByTag(tag);

        String titleTag = canvas.new_tag("x", "-title");
        Element e = new TitleElement(title, style.title_font, "title_font", titleTag);
        canvas.addElement(e);

        // set left / middle / right location
        switch (style.title_pos) {
            case bl:
            case tl:
                canvas.moveByTag(titleTag, 2 * style.padding, 0);
                break;
            case bm:
            case tm:
                canvas.moveByTag(titleTag, (bbox.f.f + bbox.s.f - e.end.f) / 2
                        - 2 * style.padding, 0);
                break;
            case br:
            case tr:
                canvas.moveByTag(titleTag, bbox.s.f - e.end.f - 2 * style.padding, 0);
                break;
        }

        // set top / bottom location
        switch(style.title_pos) {
            case tl:
            case tm:
            case tr:
                canvas.moveByTag(tag, 0, e.end.s + 2 * style.padding);
                break;
            case bl:
            case bm:
            case br:
                canvas.moveByTag(titleTag, 0, bbox.s.s + 2 * style.padding);
                break;
        }

        return canvas;
    }

    /**
     * @return tag of built Unit
     */
    private void parseDiagram(Unit unit, boolean ltor) {
        if (null == unit) {
            setUnitEndPoint(null);
            return;
        }

        setLtor(ltor);
        unit.accept(this);
    }

    /**
     * @return tag of built Unit
     */
    private UnitEndPoint getDiagramParseResult(Unit unit, boolean ltor) {
        parseDiagram(unit, ltor);
        return getUnitEndPoint();
    }


    @Override
    public void visitNoneNode(NoneNode unit) {
        String tag = this.canvas.new_tag("x", "");

        Element e = new LineElement(new Pair<>(0, 0), new Pair<>(1, 0),
                null, this.style.outline_width, tag);
        e.setStart(new Pair<>(0, 0));
        e.setEnd(new Pair<>(1, 0));
        this.canvas.addElement(e);

        setUnitEndPoint(new UnitEndPoint(tag, e.getEnd()));
    }

    @Override
    public void visitNode(Node unit) {
        String txt = unit.toString();
        NodeStyle ns = this.style.getNodeStyle(txt);
        txt = ns.extractCaptureGroupText(txt);
//        txt = ns.modify(txt);
        Font font = ns.font;
        String fontName = ns.name + "_font";

        Color fill = ns.fill;
        Color textColor = ns.text_color;

        Pair<Integer, Integer> textSize = getTextSize(txt, font);
        int x0 = -textSize.f / 2;
        int y0 = -textSize.s / 2;
        int x1 = x0 + textSize.f;
        int y1 = y0 + textSize.s;

        int h = y1 - y0 + 1;
        int rad = (h + 1) / 2;

        int lft = x0;
        int rgt = x1;
        int top = y1 - 2 * rad;

        if (ns.shape.equals("bubble") || ns.shape.equals("hex")) {
            lft += rad / 2 - 2;
            rgt -= rad / 2 - 2;
        } else {
            lft -= 5;
            rgt += 5;
        }

        if (lft > rgt) {
            lft = (x0 + x1) / 2;
            rgt = lft;
        }

        String tag = this.canvas.new_tag("x", "-box");

        Pair<Integer, Integer> start;
        Pair<Integer, Integer> end;

        BubbleElementBase b;
        String href = this.canvas.urlMap.get(txt);
        switch (ns.shape) {
            case "bubble":
                start = new Pair<>(lft - rad, top);
                end = new Pair<>(rgt + rad, y1);
                b = new BubbleElement(start, end, href, txt, new Pair<>(x0, y0), font,
                        fontName, textColor, this.style.outline_width, fill, tag);
                break;
            case "hex":
                start = new Pair<>(lft - rad, top);
                end = new Pair<>(rgt + rad, y1);
                b = new HexBubbleElement(start, end, href, txt, new Pair<>(x0, y0), font,
                        fontName, textColor, this.style.outline_width, fill, tag);
                break;
            default:
                start = new Pair<>(lft, top);
                end = new Pair<>(rgt, y1);
                b = new BoxBubbleElement(start, end, href, txt, new Pair<>(x0, y0), font,
                        fontName, textColor, this.style.outline_width, fill, tag);
                break;
        }
        this.canvas.addElement(b);

        x0 = start.f;
        x1 = end.f;

        int width = x1 - x0;

        this.canvas.moveByTag(tag, -x0, 2);

        setUnitEndPoint(new UnitEndPoint(tag, new Pair<>(width, 0)));
    }

    @Override
    public void visitBullet(Bullet unit) {
        String tag = this.canvas.new_tag("x", "");
        int w = this.style.outline_width;
        int r = w + 1;
        this.canvas.addElement(
                new OvalElement(new Pair<>(0, -r), new Pair<>(2 * r, r),
                        w, this.style.bullet_fill, tag));

        setUnitEndPoint(new UnitEndPoint(tag, new Pair<>(2 * r, 0)));
    }

    @Override
    public void visitLine(Line line) {
        boolean ltor = getLtor();
        String tag = this.canvas.new_tag("x", "");

        int sep = this.style.h_sep;
        int width = this.style.line_width;
        Pair<Integer, Integer> pos = new Pair<>(0, 0);

        int unitNum = 0;
        int unitStep = 1;
        int size = line.getUnits().size();

        if (!ltor) {
            unitNum = size - 1;
            unitStep = -1;
        }

        for (; 0 <= unitNum && unitNum < size; unitNum += unitStep) {
            Unit unit = line.getUnits().get(unitNum);
            UnitEndPoint endPoint = this.getDiagramParseResult(unit, ltor);
            if (endPoint == null) {
                continue;
            }

            if (pos.f != 0) {
                // has element before
                int xn = pos.f + sep;
                this.canvas.moveByTag(endPoint.tag, xn, pos.s);
                // create line from previous to this
                LineElement l = new LineElement(new Pair<>(pos.f - 1, pos.s), new Pair<>(xn, pos.s),
                        ltor ? "last" : "first", width, tag);
                this.canvas.addElement(l);
                pos.f = xn + endPoint.endpoint.f;
            } else {
                // first element on line
                pos.f = endPoint.endpoint.f;
            }
            pos.s = endPoint.endpoint.s;

            // tag this unit
            this.canvas.addTagByTag(tag, endPoint.tag);
            // delete old tag
            this.canvas.dropTag(endPoint.tag);
        }

        if (pos.f == 0) {
            // line is empty
            pos.f = sep * 2;
            this.canvas.addElement(
                    new LineElement(new Pair<>(0, 0), new Pair<>(sep, 0),
                            null, width, tag));
            this.canvas.addElement(
                    new LineElement(new Pair<>(sep, 0), new Pair<>(pos.f, 0),
                            "last", width, tag));
            pos.f = sep;
        }

        setUnitEndPoint(new UnitEndPoint(tag, pos));
    }

    @Override
    public void visitToploop(Toploop loop) {
        boolean ltor = getLtor();
        String tag = this.canvas.new_tag("x", "");

        int sep = this.style.v_sep;
        int vsep = sep / 2;

        // parse forward
        UnitEndPoint fEndPoint = getDiagramParseResult(loop.getForwardPart(), ltor);
        String ft = fEndPoint.tag;
        int fexx = fEndPoint.endpoint.f;
        int fexy = fEndPoint.endpoint.s;
        Pair<Pair<Integer, Integer>, Pair<Integer, Integer>> fBox = this.canvas.getBoundingBoxByTag(ft);
        int fx0 = fBox.f.f;
        int fy0 = fBox.f.s;
        int fx1 = fBox.s.f;

        // parse backward
        UnitEndPoint bEndPoint = getDiagramParseResult(loop.getBackwardPart(), !ltor);
        String bt = bEndPoint.tag;
        int bexx = bEndPoint.endpoint.f;
        int bexy = bEndPoint.endpoint.s;
        Pair<Pair<Integer, Integer>, Pair<Integer, Integer>> bBox = this.canvas.getBoundingBoxByTag(bt);
        int bx0 = bBox.f.f;
        int bx1 = bBox.s.f;
        int by1 = bBox.s.s;

        // move processes
        int fw = fx1 - fx0;
        int bw = bx1 - bx0;
        int dy = -(by1 - fy0 + vsep);
        this.canvas.moveByTag(bt, 0, dy);
        bexy += dy;
        int mxx;

        int dx = Math.abs(fw - bw) / 2;

        if (fw > bw) {
            this.canvas.moveByTag(bt, dx, 0);
            bexx += dx;
            this.canvas.addElement(
                    new LineElement(new Pair<>(0, dy), new Pair<>(dx, dy),
                            null, this.style.line_width, bt));
            this.canvas.addElement(
                    new LineElement(new Pair<>(bexx, bexy), new Pair<>(fx1, bexy),
                            (ltor || dx < 2 * vsep ? null : "first"), this.style.line_width, bt));
            mxx = fexx;
        } else if (bw > fw) {
            this.canvas.moveByTag(ft, dx, 0);
            fexx += dx;
            this.canvas.addElement(
                    new LineElement(new Pair<>(0, 0), new Pair<>(dx, fexy),
                            null, this.style.line_width, ft));
            this.canvas.addElement(
                    new LineElement(new Pair<>(fexx, fexy), new Pair<>(bx1, fexy),
                            null, this.style.line_width, ft));
            mxx = bexx;
        } else {
            mxx = fexx;
        }

        // Retag
        this.canvas.addTagByTag(tag, bt);
        this.canvas.addTagByTag(tag, ft);
        this.canvas.dropTag(bt);
        this.canvas.dropTag(ft);

        // move for left turnback
        this.canvas.moveByTag(tag, sep, 0);
        mxx += sep;
        this.canvas.addElement(
                new LineElement(new Pair<>(0, 0), new Pair<>(sep, 0),
                        null, this.style.line_width, tag));

        drawLeftTurnBack(tag, sep, 0, dy, ltor ? "up" : "down");
        drawRightTurnBack(tag, mxx, fexy, bexy, ltor ? "down" : "up");

        Pair<Pair<Integer, Integer>, Pair<Integer, Integer>> box = this.canvas.getBoundingBoxByTag(tag);
        int x1 = box.s.f;

        this.canvas.addElement(
                new LineElement(new Pair<>(mxx, fexy), new Pair<>(x1, fexy),
                        null, this.style.line_width, tag));

        setUnitEndPoint(new UnitEndPoint(tag, new Pair<>(x1, fexy)));
    }

    @Override
    public void visitLoop(Loop loop) {
        boolean ltor = getLtor();
        String tag = this.canvas.new_tag("x", "");
        int sep = this.style.v_sep;
        int vsep = sep / 2;

        // parse forward
        UnitEndPoint fEndPoint = getDiagramParseResult(loop.getForwardPart(), ltor);
        String ft = fEndPoint.tag;
        int fexx = fEndPoint.endpoint.f;
        int fexy = fEndPoint.endpoint.s;
        Pair<Pair<Integer, Integer>, Pair<Integer, Integer>> fBox = this.canvas.getBoundingBoxByTag(ft);
        int fx0 = fBox.f.f;
        int fx1 = fBox.s.f;
        int fy1 = fBox.s.s;

        // parse backward
        UnitEndPoint bEndPoint = getDiagramParseResult(loop.getBackwardPart(), !ltor);
        String bt = bEndPoint.tag;
        int bexx = bEndPoint.endpoint.f;
        int bexy = bEndPoint.endpoint.s;
        Pair<Pair<Integer, Integer>, Pair<Integer, Integer>> bBox = this.canvas.getBoundingBoxByTag(bt);
        int bx0 = bBox.f.f;
        int by0 = bBox.f.s;
        int bx1 = bBox.s.f;

        // move processes
        int fw = fx1 - fx0;
        int bw = bx1 - bx0;
        int dy = fy1 - by0 + vsep;
        this.canvas.moveByTag(bt, 0, dy);
        bexy += dy;
        int mxx;

        if (fw > bw) {
            int dx;
            if (fexx < fw && fexx >= bw) {
                dx = (fexx - bw) / 2;
                this.canvas.moveByTag(bt, dx, 0);
                bexx += dx;
                this.canvas.addElement(
                        new LineElement(new Pair<>(0, dy), new Pair<>(dx, dy),
                                null, this.style.line_width, bt));
                this.canvas.addElement(
                        new LineElement(new Pair<>(bexx, bexy), new Pair<>(fexx, bexy),
                                "first", this.style.line_width, bt));
            } else {
                dx = (fw - bw) / 2;
                this.canvas.moveByTag(bt, dx, 0);
                bexx += dx;

                this.canvas.addElement(
                        new LineElement(new Pair<>(0, dy), new Pair<>(dx, dy),
                                ltor || dx < 2 * vsep ? null : "last", this.style.line_width, bt));
                this.canvas.addElement(
                        new LineElement(new Pair<>(bexx, bexy), new Pair<>(fx1, bexy),
                                !ltor || dx < 2 * vsep ? null : "first", this.style.line_width, bt));
            }
            mxx = fexx;
        } else if (bw > fw) {
            int dx = (bw - fw) / 2;
            this.canvas.moveByTag(ft, dx, 0);
            fexx += dx;
            this.canvas.addElement(
                    new LineElement(new Pair<>(0, 0), new Pair<>(dx, fexy),
                            ltor ? "last" : "first", this.style.line_width, ft));
            this.canvas.addElement(
                    new LineElement(new Pair<>(fexx, fexy), new Pair<>(bx1, fexy),
                            null, this.style.line_width, ft));
            mxx = bexx;
        } else {
            mxx = fexx;
        }

        // retag
        this.canvas.addTagByTag(tag, bt);
        this.canvas.addTagByTag(tag, ft);
        this.canvas.dropTag(bt);
        this.canvas.dropTag(ft);

        // move for left turnback
        this.canvas.moveByTag(tag, sep, 0);

        mxx += sep;
        this.canvas.addElement(
                new LineElement(new Pair<>(0, 0), new Pair<>(sep, 0),
                        null, this.style.line_width, tag));

        drawLeftTurnBack(tag, sep, 0, dy, ltor ? "up" : "down");
        drawRightTurnBack(tag, mxx, fexy, bexy, ltor ? "down" : "up");

        int exit_x = mxx + this.style.max_radius;
        this.canvas.addElement(
                new LineElement(new Pair<>(mxx, fexy), new Pair<>(exit_x, fexy),
                        null, this.style.line_width, tag));

        setUnitEndPoint(new UnitEndPoint(tag, new Pair<>(exit_x, fexy)));
    }

    @Override
    public void visitChoice(Choice choice) {
        boolean ltor = getLtor();
        String tag = this.canvas.new_tag("x", "");

        int sep = this.style.v_sep;
        int vsep = sep / 2;

        int n = choice.getUnits().size();

        if (n == 0) {
            setUnitEndPoint(null);
            return;
        }
        ArrayList<UnitEndPoint> res = new ArrayList<>();
        int mxw = 0;

        for (int i = 0; i < n; ++i) {
            res.add(getDiagramParseResult(choice.getUnits().get(i), ltor));

            Pair<Pair<Integer, Integer>, Pair<Integer, Integer>> box =
                    this.canvas.getBoundingBoxByTag(res.get(i).tag);
            int w = box.s.f - box.f.f;
            if (i != 0) {
                w += 20;
            }
            mxw = Math.max(mxw, w);
        }

        int x2 = sep * 2;
        int x3 = mxw + x2;
        int x4 = x3 + sep;
        int x5 = x4 + sep;

        int exy = 0;
        int btm = 0;

        for (int i = 0; i < n; ++i) {
            String t = res.get(i).tag;
            int texx = res.get(i).endpoint.f;
            int texy = res.get(i).endpoint.s;
            Pair<Pair<Integer, Integer>, Pair<Integer, Integer>> box =
                    this.canvas.getBoundingBoxByTag(t);
            int w = box.s.f - box.f.f;
            int dx = (mxw - w) / 2 + x2;
            if (w > 10 && dx > x2 + 10) {
                dx = x2 + 10;
            }
            this.canvas.moveByTag(t, dx, 0);
            texx += dx;
            box = this.canvas.getBoundingBoxByTag(t);
            int ty0 = box.f.s;
            int ty1 = box.s.s;

            if (i == 0) {
                this.canvas.addElement(
                        new LineElement(new Pair<>(0, 0), new Pair<>(dx, 0),
                                ltor && dx > x2 ? "last" : null, this.style.line_width, tag));
                this.canvas.addElement(
                        new LineElement(new Pair<>(texx, texy), new Pair<>(x5 + 1, texy),
                                ltor ? null : "first", this.style.line_width, tag));
                exy = texy;
                this.canvas.addElement(
                        new ArcElement(new Pair<>(-sep, 0), new Pair<>(sep, sep * 2),
                                this.style.line_width, 90, -90, tag));
                btm = ty1;
            } else {
                int dy = Math.max(btm - ty0 + vsep, 2 * sep);
                this.canvas.moveByTag(t, 0, dy);
                texy += dy;
                if (dx > x2) {
                    this.canvas.addElement(
                            new LineElement(new Pair<>(x2, dy), new Pair<>(dx, dy),
                                    ltor ? "last" : null, this.style.line_width, tag));
                    this.canvas.addElement(
                            new LineElement(new Pair<>(texx, texy), new Pair<>(x3, texy),
                                    ltor ? null : "first", this.style.line_width, tag));
                }
                int y1 = dy - 2 * sep;
                this.canvas.addElement(
                        new ArcElement(new Pair<>(sep, y1), new Pair<>(sep + 2 * sep, dy),
                                this.style.line_width, 180, 90, tag));
                int y2 = texy - 2 * sep;
                this.canvas.addElement(
                        new ArcElement(new Pair<>(x3 - sep, y2), new Pair<>(x4, texy),
                                this.style.line_width, 270, 90, tag));
                if (i + 1 == n) {
                    this.canvas.addElement(
                            new ArcElement(new Pair<>(x4, exy), new Pair<>(x4 + 2 * sep, exy + 2 * sep),
                                    this.style.line_width, 180, -90, tag));
                    this.canvas.addElement(
                            new LineElement(new Pair<>(sep, dy - sep), new Pair<>(sep, sep),
                                    null, this.style.line_width, tag));
                    this.canvas.addElement(
                            new LineElement(new Pair<>(x4, texy - sep), new Pair<>(x4, exy + sep),
                                    null, this.style.line_width, tag));
                }
                btm = ty1 + dy;
            }

            // retag
            this.canvas.addTagByTag(tag, t);
            this.canvas.dropTag(t);
        }

        setUnitEndPoint(new UnitEndPoint(tag, new Pair<>(x5, exy)));
    }

    @Override
    public void visitOptx(Optx opt) {
        boolean ltor = getLtor();
        Choice c = new Choice(new ArrayList<>() {{
            add(new Line(opt.getUnits()));
            add(new NoneNode());
        }});

        parseDiagram(c, ltor);
    }

    @Override
    public void visitOpt(Opt opt) {
        boolean ltor = getLtor();
        Choice c = new Choice(new ArrayList<>() {{
            add(new NoneNode());
            add(new Line(opt.getUnits()));
        }});

        parseDiagram(c, ltor);
    }

    /**
     * Unobvious fact: self.indent < 0 if (stack instanceof Rightstack)
     */
    private void parseStack(Stack stack) {
        boolean ltor = getLtor();
        String tag = this.canvas.new_tag("x", "");

        int sep = this.style.v_sep * 2;
        int btm = 0;
        int n = stack.getUnits().size();
        if (n == 0) {
            setUnitEndPoint(null);
            return;
        }
        int next_bypass_y = 0;
        int bypass_x = 0;
        int bypass_y;
        int exit_x = 0;
        int exit_y = 0;
        int w;
        int e2;
        int e3;
        int ex2;
        int enter_x;
        int enter_y;
        int back_y;
        int mid_y;
        int bypass = 0;

        for (int i = 0; i < n; ++i) {
            Unit unit = stack.getUnits().get(i);
            Unit term;
            bypass_y = next_bypass_y;
            if (i != 0 && indent >= 0 && unit.getUnits().size() != 0 && unit instanceof Opt) {
                bypass = 1;
                term = new Line(unit.getUnits());
            } else {
                bypass = 0;
                term = unit;
                next_bypass_y = 0;
            }

            UnitEndPoint ep = getDiagramParseResult(term, ltor);
            String t = ep.tag;
            int exx = ep.endpoint.f;
            int exy = ep.endpoint.s;
            Pair<Pair<Integer, Integer>, Pair<Integer, Integer>> box =
                    this.canvas.getBoundingBoxByTag(t);
            int tx0 = box.f.f;
            int ty0 = box.f.s;
            int tx1 = box.s.f;

            if (i == 0) {
                exit_x = exx;
                exit_y = exy;
            } else {
                enter_y = btm - ty0 + sep * 2 + 2;
                if (bypass == 1) {
                    next_bypass_y = enter_y - this.style.max_radius;
                }
                if (indent < 0) {
                    w = tx1 - tx0;
                    enter_x = exit_x - w + sep * indent;
                    ex2 = sep * 2 - indent;
                    if (ex2 > enter_x) {
                        enter_x = ex2;
                    }
                } else {
                    enter_x = sep * 2 + indent;
                }
                back_y = btm + sep + 1;

                if (bypass_y > 0) {
                    mid_y = (bypass_y + this.style.max_radius + back_y) / 2;
                    this.canvas.addElement(
                            new LineElement(new Pair<>(bypass_x, bypass_y), new Pair<>(bypass_x, mid_y),
                                    "last", this.style.line_width, tag));
                    this.canvas.addElement(
                            new LineElement(new Pair<>(bypass_x, mid_y),
                                    new Pair<>(bypass_x, back_y + this.style.max_radius),
                                    null, this.style.line_width, tag));
                }

                this.canvas.moveByTag(t, enter_x, enter_y);
                e2 = exit_x + sep;
                this.canvas.addElement(
                        new LineElement(new Pair<>(exit_x, exit_y), new Pair<>(e2, exit_y),
                                null, this.style.line_width, tag));
                drawRightTurnBack(tag, e2, exit_y, back_y, "down");
                e3 = enter_x - sep;
                bypass_x = e3 - this.style.max_radius;
                int emid = (e2 + e3) / 2;
                this.canvas.addElement(
                        new LineElement(new Pair<>(e2, back_y), new Pair<>(emid, back_y),
                                "last", this.style.line_width, tag));
                this.canvas.addElement(
                        new LineElement(new Pair<>(emid, back_y), new Pair<>(e3, back_y),
                                null, this.style.line_width, tag));
                drawLeftTurnBack(tag, e3, back_y, enter_y, "down");
                this.canvas.addElement(
                        new LineElement(new Pair<>(e3, enter_y), new Pair<>(enter_x, enter_y),
                                "last", this.style.line_width, tag));
                exit_x = enter_x + exx;
                exit_y = enter_y + exy;
            }

            // retag
            this.canvas.addTagByTag(tag, t);
            this.canvas.dropTag(t);
            btm = this.canvas.getBoundingBoxByTag(tag).s.s;
        }

        if (bypass == 1) {
            int fwd_y = btm + sep + 1;
            mid_y = (next_bypass_y + this.style.max_radius + fwd_y) / 2;
            int descender_x = exit_x + this.style.max_radius;
            this.canvas.addElement(
                    new LineElement(new Pair<>(bypass_x, next_bypass_y), new Pair<>(bypass_x, mid_y),
                            "last", this.style.line_width, tag));
            this.canvas.addElement(
                    new LineElement(new Pair<>(bypass_x, mid_y),
                            new Pair<>(bypass_x, fwd_y - this.style.max_radius),
                            null, this.style.line_width, tag));
            this.canvas.addElement(
                    new ArcElement(new Pair<>(bypass_x, fwd_y - 2 * this.style.max_radius),
                            new Pair<>(bypass_x + 2 * this.style.max_radius, fwd_y),
                            this.style.line_width, 180, 90, tag));
            this.canvas.addElement(
                    new ArcElement(new Pair<>(exit_x - this.style.max_radius, exit_y),
                            new Pair<>(descender_x, exit_y + 2 * this.style.max_radius),
                            this.style.line_width, 90, -90, tag));
            this.canvas.addElement(
                    new ArcElement(new Pair<>(descender_x, fwd_y - 2 * this.style.max_radius),
                            new Pair<>(descender_x + 2 * this.style.max_radius, fwd_y),
                            this.style.line_width, 180, 90, tag));
            exit_x -= 2 * this.style.max_radius;
            int half_x = (exit_x + indent) / 2;
            this.canvas.addElement(
                    new LineElement(new Pair<>(bypass_x + this.style.max_radius, fwd_y),
                            new Pair<>(half_x, fwd_y),
                            "last", this.style.line_width, tag));
            this.canvas.addElement(
                    new LineElement(new Pair<>(half_x, fwd_y), new Pair<>(exit_x, fwd_y),
                            null, this.style.line_width, tag));
            this.canvas.addElement(
                    new LineElement(new Pair<>(descender_x, exit_x + this.style.max_radius),
                            new Pair<>(descender_x, fwd_y - this.style.max_radius),
                            "last", this.style.line_width, tag));
            exit_y = fwd_y;
        }

        setUnitEndPoint(new UnitEndPoint(tag, new Pair<>(exit_x, exit_y)));
    }

    @Override
    public void visitStack(Stack stack) {
        setIndent(0);
        parseStack(stack);
    }

    @Override
    public void visitRightstack(Rightstack unit) {
        setIndent(-1);
        parseStack(unit);
    }

    @Override
    public void visitIndentstack(Indentstack unit) {
        int sep = this.style.h_sep * unit.getIndent();
        setIndent(sep);
        parseStack(unit);
    }

    private void drawLeftTurnBack(String tag, int x, int y0_, int y1_, String flow) {
        int y0 = Math.min(y0_, y1_);
        int y1 = Math.max(y0_, y1_);

        if (y1 - y0 > 3 * this.style.max_radius) {
            int xr0 = x - this.style.max_radius;
            int xr1 = x + this.style.max_radius;
            this.canvas.addElement(
                    new ArcElement(new Pair<>(xr0, y0), new Pair<>(xr1, y0 + 2 * this.style.max_radius),
                            this.style.line_width, 90, 90, tag));
            int yr0 = y0 + this.style.max_radius;
            int yr1 = y1 - this.style.max_radius;
            if (Math.abs(yr1 - yr0) > 2 * this.style.max_radius) {
                int half_y = (yr0 + yr1) / 2;
                if (flow.equals("down")) {
                    this.canvas.addElement(
                            new LineElement(new Pair<>(xr0, yr0), new Pair<>(xr0, half_y),
                                    "last", this.style.line_width, tag));
                    this.canvas.addElement(
                            new LineElement(new Pair<>(xr0, half_y), new Pair<>(xr0, yr1),
                                    null, this.style.line_width, tag));
                } else {
                    this.canvas.addElement(
                            new LineElement(new Pair<>(xr0, yr1), new Pair<>(xr0, half_y),
                                    "last", this.style.line_width, tag));
                    this.canvas.addElement(
                            new LineElement(new Pair<>(xr0, half_y), new Pair<>(xr0, yr0),
                                    null, this.style.line_width, tag));
                }
            } else {
                this.canvas.addElement(
                        new LineElement(new Pair<>(xr0, yr0), new Pair<>(xr0, yr1),
                                null, this.style.line_width, tag));
            }

            this.canvas.addElement(
                    new ArcElement(new Pair<>(xr0, y1 - 2 * this.style.max_radius), new Pair<>(xr1, y1),
                            this.style.line_width, 180, 90, tag));
        } else {
            int r = (y1 - y0) / 2;
            int x0 = x - r;
            int x1 = x + r;
            this.canvas.addElement(
                    new ArcElement(new Pair<>(x0, y0), new Pair<>(x1, y1),
                            this.style.line_width, 90, 180, tag));
        }
    }

    private void drawRightTurnBack(String tag, int x, int y0_, int y1_, String flow) {
        int y0 = Math.min(y0_, y1_);
        int y1 = Math.max(y0_, y1_);

        if (y1 - y0 > 3 * this.style.max_radius) {
            int xr0 = x - this.style.max_radius;
            int xr1 = x + this.style.max_radius;

            this.canvas.addElement(
                    new ArcElement(new Pair<>(xr0, y0), new Pair<>(xr1, y0 + 2 * this.style.max_radius),
                            this.style.line_width, 90, -90, tag));
            int yr0 = y0 + this.style.max_radius;
            int yr1 = y1 - this.style.max_radius;

            if (Math.abs(yr1 - yr0) > 2 * this.style.max_radius) {
                int half_y = (yr1 + yr0) / 2;
                if (flow.equals("down")) {
                    this.canvas.addElement(
                            new LineElement(new Pair<>(xr1, yr0), new Pair<>(xr1, half_y),
                                    "last", this.style.line_width, tag));
                    this.canvas.addElement(
                            new LineElement(new Pair<>(xr1, half_y), new Pair<>(xr1, yr1),
                                    null, this.style.line_width, tag));
                } else {
                    this.canvas.addElement(
                            new LineElement(new Pair<>(xr1, yr1), new Pair<>(xr1, half_y),
                                    "last", this.style.line_width, tag));
                    this.canvas.addElement(
                            new LineElement(new Pair<>(xr1, half_y), new Pair<>(xr1, yr0),
                                    null, this.style.line_width, tag));
                }
            } else {
                this.canvas.addElement(
                        new LineElement(new Pair<>(xr1, yr0), new Pair<>(xr1, yr1),
                                null, this.style.line_width, tag));
            }

            this.canvas.addElement(
                    new ArcElement(new Pair<>(xr0, y1 - 2 * this.style.max_radius), new Pair<>(xr1, y1),
                            this.style.line_width, 0, -90, tag));
        } else {
            int r = (y1 - y0) / 2;
            int x0 = x - r;
            int x1 = x + r;
            this.canvas.addElement(
                    new ArcElement(new Pair<>(x0, y0), new Pair<>(x1, y1),
                            this.style.line_width, 90, -180, tag));
        }
    }

    public static Pair<Integer, Integer> getTextSize(String text, Font font) {

        Rectangle2D r = font.getStringBounds(text,
                new FontRenderContext(null, true, true));

        return new Pair<>((int) (r.getMaxX() - r.getMinX() + text.length() + 10),
                (int) (r.getMaxY() - r.getMinY() + 10));
    }

    /**
     * @brief class for return in parse functions
     * @details contain pair of tag end endpoint
     */
    public static class UnitEndPoint {
        UnitEndPoint(String tag, Pair<Integer, Integer> endpoint) {
            this.tag = tag;
            this.endpoint = endpoint;
        }

        public String tag;
        public Pair<Integer, Integer> endpoint;
    }

    public UnitEndPoint getUnitEndPoint() {
        return unitEndPoint;
    }

    public void setUnitEndPoint(UnitEndPoint unitEndPoint) {
        this.unitEndPoint = unitEndPoint;
    }

    public boolean getLtor() {
        return ltor;
    }

    public void setLtor(boolean ltor) {
        this.ltor = ltor;
    }

    public Integer getIndent() {
        return indent;
    }

    public void setIndent(Integer indent) {
        this.indent = indent;
    }

    private UnitEndPoint unitEndPoint;
    private boolean ltor;
    private Integer indent;
    private String title;
}
