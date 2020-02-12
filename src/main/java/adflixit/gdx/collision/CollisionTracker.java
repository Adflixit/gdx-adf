package adflixit.gdx.collision;

import static adflixit.gdx.Util.*;
import static adflixit.gdx.collision.CollisionUtils.*;

import adflixit.gdx.Pool;
import adflixit.gdx.Poolable;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Vector2;
import java.util.Comparator;

public class CollisionTracker {
  private class Entry extends Poolable {
    public Collision c = new Collision();

    public Entry() {}

    public void set(Collideable a, Collideable b) {
      free = false;
      c.a = a;
      c.b = b;
    }

    public boolean match(Collideable a, Collideable b) {
      return (c.a==a&&c.b==b) || (c.b==a&&c.a==b);
    }
  }

  private class RayCastPoint extends Poolable {
    public Collideable body;
    public final Vector2 pos = new Vector2(), normal = new Vector2();

    public void set(Collideable c, Vector2 p, double a) {
      free = false;
      body = c;
      pos.set(p);
      normal.set(cosf(a), sinf(a));
    }
  }

  private class EntryPool extends Pool<Entry> {
    @Override public EntryPool init(int cap) {
      resize(cap);
      return this;
    }

    @Override public EntryPool init() {
      return init(10);
    }

    @Override public Entry newObj() {return new Entry();}
    @Override public boolean isFree(Entry obj) {return obj.isFree();}
    @Override public void reset(Entry obj) {obj.release();}

    public Entry match(Collideable a, Collideable b) {
      for (Entry ent : entries) {
        if (ent.match(a, b)) {
          return ent;
        }
      }
      return null;
    }
  }

  private abstract class ClPool<E extends Collideable> extends Pool<E> {
    @Override public E newObj() {return null;}
    @Override public boolean isFree(E obj) {return obj.isFree();}
    @Override public void reset(E obj) {obj.release();}
  }

  private final Pool<Box>     boxes     = new ClPool<Box>() {
    @Override public Box newObj() {return new Box();}
  }.init();
  private final Pool<Line>    lines     = new ClPool<Line>() {
    @Override public Line newObj() {return new Line();}
  }.init();
  private final Pool<Circle>  circles   = new ClPool<Circle>() {
    @Override public Circle newObj() {return new Circle();}
  }.init();
  private final EntryPool     entries   = new EntryPool().init();

  private CollisionListener   listener;
  private boolean             isDormant = true;
  private int                 rigidBodies;  // number of bodies which may collide
  private final Vector2       cVel      = new Vector2();  // equalized collision velocity

  private RayCastCallback     rcCallback;   // ray cast callback
  private boolean             rcActive;
  private boolean             rcBoxesDone;
  private boolean             rcLinesDone;
  private boolean             rcCirclesDone;
  private final Line          rcRay     = new Line();
  private final Line          rcDummy   = new Line();

  /**
   * All intersection points in the current ray cast
   */
  private final Pool<RayCastPoint> rcPoints = new Pool<RayCastPoint>() {
    @Override public RayCastPoint newObj() {return new RayCastPoint();}
    @Override public boolean isFree(RayCastPoint obj) {return obj.isFree();}
  }.init();  

  private final Comparator<RayCastPoint> rcComparator = (a, b) -> (int)(dist(rcRay.p1, a.pos) - dist(rcRay.p1, b.pos));

  public CollisionTracker() {}

  public CollisionTracker(CollisionListener listener) {
    init(listener);
  }

  public void init(CollisionListener listener) {
    this.listener = listener;
  }

  public void rayCast(RayCastCallback cb, float x1, float y1, float x2, float y2) {
    if (cb == null) {
      throw new IllegalArgumentException("Ray cast callback can't be null.");
    }
    rcActive = true;
    rcCallback = cb;
    rcRay.set(x1, y1, x2, y2);
  }

  public void rayCast(RayCastCallback cb, Vector2 p1, Vector2 p2) {
    rayCast(cb, p1.x, p1.y, p2.x, p2.y);
  }

  private void rcReset() {
    rcActive = false;
    rcPoints.resetSize();
  }

  private void rcCheckBoxIntersection(Box box) {
    for (int i=0; i<4; i++) {
      float x1 = i==0||i==3 ? box.x1() : box.x2(),
          y1 = i<2 ? box.y2() : box.y1(),
          x2 = i==1||i==2 ? box.x2() : box.x1(),
          y2 = i>1 ? box.y1() : box.y2();

      rcDummy.set(x1, y1, x2, y2);

      if (lineIntersectionPoint(rcRay, rcDummy, tmpv2)) {
        rcPoints.nextFreeExtra().set(box, tmpv2, angleBetweenLines(rcRay, rcDummy));
      }
    }
  }

  private void rcCheckLineIntersection(Line line) {
    lineIntersectionPoint(rcRay, line, rcPoints.nextFreeExtra().pos);
  }

  private void rcProcessIntersectionPoints() {
    rcPoints.sort(rcComparator);
    float rayLength = dist(rcRay.p1, rcRay.p2);
    for (RayCastPoint p : rcPoints) {
      if (!rcCallback.call(p.body, p.pos, p.normal, dist(rcRay.p1, p.pos) / rayLength)) {
        rcActive = false;
        return;
      }
    }
  }

  private boolean checkCollision(boolean cond, Collideable a, Collideable b) {
    if (hasFlag(a.filter, b.mask)) {
      if (cond && !isCollided(a, b)) {
        listener.beginCollision(captureCollision(a, b).c);
        return true;
      } else if (isCollided(a, b)) {
        listener.endCollision(entries.match(a, b).c);
        releaseCollision(a, b);
      }
    }
    return false;
  }

  public void update() {
    if (listener == null) {
      throw new RuntimeException("Collision listener has to be initialized first.");
    }
    if (isDormant) {
      return;
    }

    for (Box i : boxes) {
      // check box on box
      for (Box j : boxes) {
        // not checking the same box
        if (i==j) {
          continue;
        }

        if (checkCollision(testBoxIntersection(i,j), i,j)) {
          boxIntersectionNormal(i,j, tmpv2);
          clamp(j.mass(), 0, j.mass());
          float m = j.mass() > 0 ? 2 : i.mass()/j.mass();
          if (j.mass() > 0) {
            cVel.set(i.vx()-j.vx(), i.vy()-j.vy());
            cVel.add(tmpv2);
            cVel.scl(1/m);
            i.vel.set(cVel);
            j.pos.add(cVel);
          } else {
            i.setVel(0,0);
          }
        }
      }
      i.pos.add(i.vx(), i.vy());
      // check box on line
      for (Line j : lines) {
        if (checkCollision(testBoxLineIntersection(i,j), i,j)) {
          
        }
      }
      // ray casting
      if (rcActive && !rcBoxesDone) {
        // first check if the box and the ray overlap
        if (testBoxLineIntersection(i, rcRay)) {
          // next disperse the box to lines and record the intersection points
          rcCheckBoxIntersection(i);
          // sort the detected intersection points in the appearance order and poll each
          rcProcessIntersectionPoints();
        } else {
          rcBoxesDone = true;
        }
      }
    }
    for (Line i : lines) {
      for (Line j : lines) {
        checkCollision(testLineIntersection(i,j), i,j);
      }
      // ray casting
      if (rcActive && !rcLinesDone) {
        rcCheckLineIntersection(i);
        rcProcessIntersectionPoints();
      } else {
        rcLinesDone = true;
      }
    }
    if (rcBoxesDone && rcLinesDone) {
      rcReset();
    }
  }

  public void drawDebug(ShapeRenderer shr) {
    shr.begin(ShapeType.Line);
    for (Box i : boxes) {
      shr.rect(i.x1(), i.y1(), i.width(), i.height());
    }
    for (Line i : lines) {
      shr.line(i.x1(), i.y1(), i.x2(), i.y2());
    }
    for (Circle i : circles) {
      shr.circle(i.x(), i.y(), i.rad());
    }
    shr.end();
  }

  private void checkDormancy() {
    isDormant = boxes.first().isFree() && lines.first().isFree() && circles.first().isFree();
  }

  private Entry captureCollision(Collideable a, Collideable b) {
    Entry ent = entries.nextFreeExtra();
    ent.set(a, b);
    return ent;
  }

  private void releaseCollision(Collideable a, Collideable b) {
    Entry ent = entries.match(a, b);
    if (ent != null) {
      ent.release();
    }
  }

  private boolean isCollided(Collideable a, Collideable b) {
    return entries.match(a, b) != null;
  }

  public Box createBox(boolean sensor) {
    Box box = boxes.nextFreeExtra();
    box.init(boxes.indexOf(box));

    box.isSensor = sensor;
    if (!sensor) {
      rigidBodies++;
    }

    checkDormancy();
    return box;
  }

  public Box createBox() {
    return createBox(true);
  }

  public Line createLine() {
    Line line = lines.nextFreeExtra();
    line.init(lines.indexOf(line));
    checkDormancy();
    return line;
  }

  public Circle createCircle(boolean sensor) {
    Circle circle = circles.nextFreeExtra();
    circle.init(circles.indexOf(circle));

    circle.isSensor = sensor;
    if (!sensor) {
      rigidBodies++;
    }

    checkDormancy();
    return circle;
  }

  public Circle createCircle() {
    return createCircle(true);
  }

  public void setSensor(Box box, boolean sensor) {
    if (box.isSensor && !sensor) {
      rigidBodies++;
    } else if (!box.isSensor && sensor) {
      rigidBodies--;
    }
    box.isSensor = sensor;
  }

  public void setSensor(Circle circle, boolean sensor) {
    if (circle.isSensor && !sensor) {
      rigidBodies++;
    } else if (!circle.isSensor && sensor) {
      rigidBodies--;
    }
    circle.isSensor = sensor;
  }

  public void destroy(Box box) {
    box.release();
    if (!box.isSensor) {
      rigidBodies--;
    }
    checkDormancy();
  }

  public void destroy(Line line) {
    line.release();
    checkDormancy();
  }

  public void destroy(Circle circle) {
    circle.release();
    if (!circle.isSensor) {
      rigidBodies--;
    }
    checkDormancy();
  }

  public void clear() {
    for (Box i : boxes) {
      i.release();
    }
    for (Line i : lines) {
      i.release();
    }
    for (Entry i : entries) {
      i.release();
    }
    checkDormancy();
  }
}