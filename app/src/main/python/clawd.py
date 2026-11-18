"""Clawd sprites and movement, adapted from clawd_pet 1.5 by @itskotovski."""

import math
import random
import re
import time

from android.graphics import Bitmap, Canvas, Paint
from android.graphics.drawable import GradientDrawable
from android.hardware import Sensor, SensorEventListener, SensorManager
from android.content import Context
from android.util import TypedValue
from android.view import MotionEvent, Surface, View
from android.view.animation import OvershootInterpolator, PathInterpolator
from android.widget import FrameLayout, ImageView, TextView
from java import dynamic_proxy, jint
from java.lang import Runnable
from android.util import Log


class R(dynamic_proxy(Runnable)):
    def __init__(self, callback):
        super().__init__()
        self.callback = callback

    def run(self):
        self.callback()


def log(message):
    Log.w("Clawd", message)


__id__ = "clawd_pet"

GRID_W = 18
GRID_H = 13
SIZE_ITEMS = ["Маленький", "Средний", "Большой"]
SIZE_DP = [3.0, 4.0, 5.0]

PALETTES = [
    ("Claude", 0xFFD97757, 0xFF1A1A1A),
    ("Мятный", 0xFF4FBF98, 0xFF12241D),
    ("Лавандовый", 0xFF9B8AEE, 0xFF1B1631),
    ("Небесный", 0xFF4FA8E8, 0xFF0E1C29),
    ("Розовый", 0xFFEE85AE, 0xFF2A1220),
    ("Графит", 0xFF5E6670, 0xFFF2F2F2),
]
SLEEP_ITEMS = ["Через 1 минуту", "Через 3 минуты", "Через 10 минут", "Никогда"]
SLEEP_SECONDS = [60, 180, 600, 0]

HEART_COLOR = 0xFFFF5C7A
ZZZ_COLOR = 0xFF8FA3BF
SPARK_COLOR = 0xFFFFC94D
NOTE_COLOR = 0xFF6FB8FF
POOP_COLORS = {"X": 0xFF8A5A33, "o": 0xFFB27D4E, "w": 0xFFFFFFFF, "k": 0xFF1A1A1A}

PATTERNS = {
    "heart": (
        ".XX.XX.",
        "XXXXXXX",
        "XXXXXXX",
        ".XXXXX.",
        "..XXX..",
        "...X...",
    ),
    "zzz": (
        "XXXXX",
        "...X.",
        "..X..",
        ".X...",
        "XXXXX",
    ),
    "spark": (
        "..X..",
        "..X..",
        "XX.XX",
        "..X..",
        "..X..",
    ),
    "note": (
        "..XXXXX",
        "..X...X",
        "..X...X",
        "..X...X",
        "XXX.XXX",
        "XXX.XXX",
    ),
    "poop": (
        "....X....",
        "...XXo...",
        "..XXXXX..",
        ".XXXXXXo.",
        ".XwkXwkX.",
        "XXXXXXXXo",
        "XXXXXXXXX",
    ),
}

PHRASES = {
    "hello": ["Привет!", "Я Clawd!", "О, это ты!"],
    "idle": [
        "Хм…",
        "Можно я тут посижу?",
        "Пип-пип!",
        "*оглядывается*",
    ],
    "tap": ["Ой!", "Эй!", "Чего?", "Я тут!", "Хи!"],
    "annoyed": ["Хватит тыкать!", "Щекотно же!", "Ну всё, обиделся", "Эй, полегче!"],
    "pet": ["Мрр…", "Ещё!", "Приятно ♥", "Хи-хи", "Лучший день!", "♥♥♥"],
    "drag": ["Ааа!", "Куда мы?", "Поставь меня!"],
    "throw": ["Уиии!", "Я лечу!", "Ваааа!"],
    "dizzy": ["Голова кружится…", "@_@", "Где я?"],
    "wake": ["А? Что?", "Я не спал!", "*зевает*"],
    "jump": ["Хоп!", "Йе!", "Ура!"],
    "snack": ["Ням!", "Хрум-хрум", "Ещё кусочек?"],
    "mail": ["Тебе пишут!", "Сообщение!", "Кто-то написал!", "Динь-динь!", "Смотри, новенькое!"],
    "love": ["Ааа, мило ♥", "♥♥♥", "Как трогательно!", "Любовь!"],
    "laugh": ["Ахаха!", "Хи-хи-хи", "Смешно!", "Ха-ха!"],
    "hi": ["Привет-привет!", "И тебе привет!", "Здрасьте!", "*машет*"],
    "music": ["О, музыка!", "Танцуем!", "Это мой трек!", "♪♪♪"],
    "music_end": ["Эх, закончилось", "Ещё!", "Хороший трек"],
    "shake": ["Аааа!", "Землетрясение!", "Хватит трясти!", "Уоооу!"],
    "slide": ["Скользко!", "Уиии~", "Меня несёт!"],
    "hang": ["Держусь!", "Помогите!", "Высоковато…", "Не отпущу!"],
    "hang_fall": ["Ой-ой-ой!", "Не удержался!", "Падаю!"],
    "peek": ["Ку-ку!", "Я тебя вижу", "*подглядывает*", "Ты тут?"],
    "found": ["Нашёл меня!", "Эх, раскусил", "Я просто смотрел!"],
    "poop": ["Упс…", "Это не я!", "Ой…", "*краснеет*"],
    "clean": ["Спасибо!", "Прости…", "Больше не буду!", "Ты лучший"],
}

KEYWORDS = (
    ("love", re.compile(r"❤|♥|💕|💖|😘|😍|🥰|\bлюблю\b|\bобнима\w*|\bцелую\b|\blove\b")),
    ("laugh", re.compile(r"(?:ха){2,}|(?:хе){2,}|(?:хи){2,}|\bлол\b|\blol\b|\bржу\w*|😂|🤣")),
    ("hi", re.compile(r"\b(?:привет\w*|здравствуй\w*|хай|салют|hello|hi|hey)\b")),
)
MAX_KEYWORD_TEXT = 400
MAX_SCAN_MESSAGES = 10

MAX_PARTICLES = 14
MAX_FRAME_CACHE = 48

MAX_POOPS = 3
POOP_EVERY = (3, 5)

EARTH_G = 9.81
SHAKE_JERK = 14.0
SHAKE_HITS = 4
SHAKE_WINDOW = 0.9
TILT_DEAD = 0.22

PEEK_AFTER = 45.0
PEEK_HIDDEN_UNITS = 3
PEEK_OUT_UNITS = 7


def as_java_color(color: int) -> int:
    value = int(color) & 0xFFFFFFFF
    return value - 0x100000000 if value >= 0x80000000 else value


def mix_color(a: int, b: int, t: float) -> int:
    out = 0xFF000000
    for shift in (16, 8, 0):
        ca = (a >> shift) & 0xFF
        cb = (b >> shift) & 0xFF
        out |= int(ca + (cb - ca) * t) << shift
    return out


def clamp(value, lo, hi):
    if hi < lo:
        return lo
    return lo if value < lo else hi if value > hi else value


class PetTouchListener(dynamic_proxy(View.OnTouchListener)):
    def __init__(self, plugin):
        super().__init__()
        self.plugin = plugin

    def onTouch(self, view, event):
        try:
            return bool(self.plugin._on_touch(event))
        except Exception as e:
            log(f"[{__id__}] touch error: {e}")
            return False


class ShakeListener(dynamic_proxy(SensorEventListener)):
    def __init__(self, plugin):
        super().__init__()
        self.plugin = plugin

    def onSensorChanged(self, event):
        try:
            values = event.values
            self.plugin._on_accel(float(values[0]), float(values[1]), float(values[2]))
        except Exception as e:
            log(f"[{__id__}] sensor error: {e}")

    def onAccuracyChanged(self, sensor, accuracy):
        pass


class PoopClickListener(dynamic_proxy(View.OnClickListener)):
    def __init__(self, plugin):
        super().__init__()
        self.plugin = plugin

    def onClick(self, view):
        try:
            self.plugin._clean_poop(view)
        except Exception as e:
            log(f"[{__id__}] poop click error: {e}")



def create(host):
    return Clawd(host)


class Clawd:
    def __init__(self, host):
        self.host = host
        self._handler = host.getHandler()
        self._tick_r = R(self._tick)
        self._running = False
        self._paused = False
        self._unloaded = False
        self._cfg = {}
        self._decor = host.getEffects()
        self._pet = None
        self._bubble = None
        self._sensor_manager = None
        self._sensor_listener = None
        self._poop_listener = PoopClickListener(self)
        self._poops = []
        self._poop_origins = []
        self._particles = []
        self._unit = self._w = self._h = 1
        self._frames = {}
        self._particle_bitmaps = {}
        self._frame_key = None
        self._coat_transition = None
        self._reset_state()
        self._reload_cfg()
        self._update_metrics()
        self._pet = host.createPetSprite()
        self._pet.setScaleType(ImageView.ScaleType.FIT_XY)
        self._pet.setContentDescription("Clawd")
        self._pet.setClickable(True)
        self._touch_listener = PetTouchListener(self)
        self._pet.setOnTouchListener(self._touch_listener)
        self.host.addSprite(self._pet, self._w, self._h)

        self._bubble = TextView(host.getUiContext())
        self._bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.0)
        self._bubble.setMaxWidth(self.host.dp(220))
        self._bubble.setPadding(self.host.dp(12), self.host.dp(7), self.host.dp(12), self.host.dp(7))
        self._bubble.setVisibility(View.GONE)
        self._decor.addView(self._bubble, FrameLayout.LayoutParams(-2, -2))
        self._running = True
        self._greet_pending = True
        self._start_sensor()
        self._schedule(16)

    def get_setting(self, key, default):
        if isinstance(default, bool):
            return self.host.getPrefs().getBoolean(key, default)
        return self.host.getPrefs().getInt(key, -1 if default is None else default)

    def set_setting(self, key, value):
        self.host.getPrefs().edit().putInt(key, value).apply()

    def config_changed(self, key):
        old = self._cfg.copy()
        previous_coat = self._current_coat(time.monotonic()) if key == "color" else None
        self._reload_cfg()
        if key == "size":
            self._rebuild_sprites()
        elif key == "color":
            self._frames.clear()
            self._frame_key = None
            if not self._paused and self.host.animationsEnabled():
                direction = -1 if self._look < 0 else 1
                self._coat_transition = (time.monotonic(), previous_coat, direction)
            else:
                self._coat_transition = None
        elif key == "poop" and not self._cfg["poop"]:
            self._poop_due = 0.0
            self._remove_poops()
        elif key in ("shake", "gravity"):
            if self._cfg["shake"] or self._cfg["gravity"]:
                self._start_sensor()
            else:
                self._stop_sensor()
            if not self._cfg["gravity"]:
                self._platform = None
                self._slide_v = 0.0
            elif not old["gravity"]:
                self._platform_scan = 0.0
        elif key in ("ui_platforms", "phone_platforms"):
            self._platform_scan = 0.0
            self._next_phone_perch = 0.0
        elif key == "phrases" and not self._cfg["phrases"]:
            self._hide_bubble()
        elif key == "sleep" and self._state == "sleep":
            self._state = "idle"
            self._last_interaction = time.monotonic()
        if not self._paused:
            self._schedule(0)

    def pause(self, paused):
        if self._paused == paused:
            return
        self._paused = paused
        if paused:
            self._handler.removeCallbacks(self._tick_r)
            self.host.cancelFrame()
            self.host.idleFrames()
            self._stop_sensor()
            self._hide_bubble()
            self._touch = None
            if self._state in ("press", "drag", "pet"):
                self._state = "idle"
                self._save_position()
        else:
            self._last_tick = time.monotonic()
            self._bounds_cache = None
            self._start_sensor()
            self._schedule(0)

    def close(self):
        self._unloaded = True
        self._running = False
        self._handler.removeCallbacks(self._tick_r)
        self.host.cancelFrame()
        self.host.idleFrames()
        self._stop_sensor()
        self._remove_poops()
        self._decor.removeAllViews()
        self._particles.clear()
        self.host.removeSprite(self._pet)
        self._pet = None
        self._bubble = None
        self._decor = None

    def snack(self):
        if self._paused or not self._pos_ready:
            return
        now = time.monotonic()
        self._wake_for_event(now)
        self._mouth_open_until = now + 0.32
        self._chew_until = now + 1.4
        self._next_action = now + 3.0
        self._set_expr("happy", 1.8)
        self._hop(self._dp(360))
        self._spawn_particle("heart")
        self._say("snack")
        self.set_setting("snacks", self.get_setting("snacks", 0) + 1)
        if self._cfg["poop"]:
            meter = self.get_setting("poop_meter", 0) + 1
            if meter >= self._poop_need:
                meter = 0
                self._poop_need = random.randint(*POOP_EVERY)
                self._poop_due = now + random.uniform(5.0, 10.0)
            self.set_setting("poop_meter", meter)
        self._schedule(0)

    def notice(self, text):
        if self._can_react():
            self._react_incoming(self._match_keyword([str(text)[:MAX_KEYWORD_TEXT].lower()]), False)

    def _bounds(self, now):
        geometry = self.host.getGeometryVersion()
        key = (geometry, self._w, self._h)
        if self._bounds_cache is not None and self._bounds_key == key:
            return self._bounds_cache
        self._bounds_key = key
        width, height = self._decor.getWidth(), self._decor.getHeight()
        self._rotation = self.host.getRotation()
        top = self.host.getInsetTop()
        self._bounds_cache = (
            float(self.host.getInsetLeft()),
            float(max(self.host.getInsetLeft(), width - self._w - self.host.getInsetRight())),
            float(top),
            float(max(top, height - self.host.getInsetBottom() - self._h)),
            width,
            height,
        )
        return self._bounds_cache

    def platforms_changed(self):
        if self._platform == 1:
            self._platform = None
        self._platform_scan = 0.0

    def land_on_manual_platform(self):
        if self._paused or not self._pos_ready or not self._cfg["gravity"]:
            return
        platform = self._platform_rect(1)
        if platform is None:
            return
        min_x, max_x, min_y, max_y, _, _ = self._bounds(time.monotonic())
        x0, x1, top = platform
        y = top - self._platform_feet()
        if not min_y <= y <= max_y:
            return
        self._x = clamp((x0 + x1 - self._w) / 2, min_x, max_x)
        self._y = y
        self._hop_active = False
        self._hop_off = 0.0
        self._slide_v = 0.0
        self._platform_span = (x0, x1)
        self._land(time.monotonic(), 1)
        self._schedule(0)

    def _platform_feet(self):
        return self._h - self._unit

    def _platform_rect(self, index):
        if index != 1 and index < 6 and not self._cfg.get("ui_platforms", True):
            return None
        bounds = self.host.getPlatform(index)
        return None if bounds is None else (float(bounds[0]), float(bounds[1]), float(bounds[2]))

    def _scan_platforms(self, min_y, max_y):
        result = []
        for index in range(self.host.getPlatformCount()):
            rect = self._platform_rect(index)
            if rect is None:
                continue
            x0, x1, top = rect
            feet = self._platform_feet()
            if min_y + feet <= top <= max_y + feet:
                result.append((index, x0, x1, top))
        return result

    def _phone_geometry_available(self):
        return self._cfg.get("phone_platforms", False) and self.host.hasPhonePlatforms()

    def _gravity_enabled(self):
        return self._cfg.get("gravity", False) or self._phone_geometry_available()

    def _grab_phone_ledge(self, now, min_y, max_y):
        feet = self._y + self._platform_feet()
        nearest = None
        for index, left, right, top in self._scan_platforms(min_y, max_y):
            if index < 6 or not self._feet_on(left, right):
                continue
            distance = abs(feet - top)
            if distance <= self._dp(38) and (nearest is None or distance < nearest[0]):
                nearest = (distance, index, left, right, top)
        if nearest is None:
            return False
        _, index, left, right, top = nearest
        self._y = top - self._platform_feet()
        self._platform_span = (left, right)
        self._land(now, index)
        return True

    def _try_phone_perch(self, now, min_x, max_x, min_y, max_y):
        if now < self._next_phone_perch or not self._phone_geometry_available():
            return False
        self._next_phone_perch = now + 2.4
        if not self._cfg.get("wander", True):
            return False
        feet = self._y + self._platform_feet()
        extra = self._dp(19)
        best = None
        for index, left, right, top in self._scan_platforms(min_y, max_y):
            if index < 6:
                continue
            rise = feet - top
            if not self._dp(26) <= rise <= self._dp(272) or top - self._platform_feet() < min_y + extra:
                continue
            target = clamp((left + right - self._w) / 2, min_x, max_x)
            travel = target - self._x
            if abs(travel) > self._dp(190):
                continue
            score = abs(travel) + .48 * abs(rise - self._dp(114))
            if best is None or score < best[0]:
                best = (score, target, rise)
        if best is None:
            return False
        _, target, rise = best
        # Aim to cross the ledge while descending, accounting for horizontal damping.
        gravity = self._dp(2600)
        upward = math.sqrt(2 * gravity * (rise + extra))
        travel_time = (upward + math.sqrt(2 * gravity * extra)) / gravity
        velocity = (target - self._x) * .35 / (1 - math.exp(-.35 * travel_time))
        self._fall(velocity - self._slide_v)
        self._vy = -upward
        self._look = 1 if target >= self._x else -1
        self._next_phone_perch = now + 5.0
        return True

    def _impact_effect(self, x, y, nx, ny, speed, landing=False):
        if self._bounds_cache is None:
            return
        floor = self._bounds_cache[3] + self._h
        if landing:
            floor = min(floor, y)
        color = jint(as_java_color(self._palette()[1]))
        self.host.emitImpact(float(x), float(y), float(nx), float(ny), float(speed),
                             float(floor), color, bool(landing))

    def _poll_media(self, now):
        if now < self._media_check:
            return
        self._media_check = now + 0.5
        kind = "music" if self._cfg["dance"] and self.host.isMusicActive() else None
        if kind == self._media:
            return
        self._media = kind
        self._last_interaction = now
        if kind:
            self._next_note = now + 0.3
            self._wake_for_event(now)
            if self._state in ("idle", "walk"):
                self._say("music")
        else:
            self._next_action = now + random.uniform(2.0, 4.0)
            if self._state == "idle" and random.random() < 0.5:
                self._say("music_end")

    def _reload_cfg(self):
        self._cfg = {
            "size": int(self.get_setting("size", 1) or 0),
            "color": int(self.get_setting("color", 0) or 0),
            "gravity": bool(self.get_setting("gravity", False)),
            "ui_platforms": bool(self.get_setting("ui_platforms", True)),
            "phone_platforms": bool(self.get_setting("phone_platforms", False)),
            "wander": bool(self.get_setting("wander", True)),
            "phrases": bool(self.get_setting("phrases", True)),
            "sleep": int(self.get_setting("sleep", 1) or 0),
            "haptics": bool(self.get_setting("haptics", True)),
            "tg_events": bool(self.get_setting("tg_events", False)),
            "dance": bool(self.get_setting("dance", True)),
            "poop": bool(self.get_setting("poop", True)),
            "shake": bool(self.get_setting("shake", True)),
        }


    def _start_sensor(self):
        if (
            self._sensor_listener is not None
            or not self._running
            or self._paused
            or self._unloaded
            or not (self._cfg["shake"] or self._cfg["gravity"])
            or self._pet is None
        ):
            return
        try:
            manager = self._pet.getContext().getSystemService(Context.SENSOR_SERVICE)
            sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) if manager is not None else None
            if sensor is None:
                return
            listener = ShakeListener(self)
            if manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI):
                self._sensor_manager = manager
                self._sensor_listener = listener
                self._accel_prev = None
                self._shake_hits = []
        except Exception as e:
            log(f"[{__id__}] sensor start error: {e}")


    def _stop_sensor(self):
        manager = self._sensor_manager
        listener = self._sensor_listener
        self._sensor_manager = None
        self._sensor_listener = None
        self._tilt = 0.0
        if manager is None or listener is None:
            return
        try:
            manager.unregisterListener(listener)
        except Exception as e:
            log(f"[{__id__}] sensor stop error: {e}")


    def _update_metrics(self):
        size = clamp(int(self._cfg.get("size", 1)), 0, len(SIZE_DP) - 1)
        self._unit = max(2, self.host.dp(SIZE_DP[size]))
        self._w = GRID_W * self._unit
        self._h = GRID_H * self._unit


    def _rebuild_sprites(self):
        old_w, old_h = self._w, self._h
        self._frames = {}
        self._particle_bitmaps = {}
        self._frame_key = None
        self._update_metrics()
        if self._pet is None:
            return
        self._x += (old_w - self._w) / 2.0
        self._y += old_h - self._h
        self.host.resizeSprite(self._pet, self._w, self._h)
        self._hop(self.host.dp(300))
        self._schedule(16)


    def _palette(self):
        idx = clamp(int(self._cfg.get("color", 0)), 0, len(PALETTES) - 1)
        return PALETTES[idx]


    def _get_frame(self, key):
        bmp = self._frames.get(key)
        if bmp is None:
            if len(self._frames) >= MAX_FRAME_CACHE:
                self._frames.pop(next(iter(self._frames)))
            bmp = self._render_frame(key)
            self._frames[key] = bmp
        return bmp


    def _current_coat(self, now):
        target = self._palette()[1:]
        if self._coat_transition is None:
            return (target,) * GRID_W
        start, origin, direction = self._coat_transition
        if now >= start + 0.66:
            self._coat_transition = None
            return (target,) * GRID_W
        progress = clamp((now - start) / 0.66, 0.0, 1.0)

        # Color the same sprite cells in order, keeping their composited colors if a new wash starts.
        # Exponential In Out, with a short per-column blend along the moving front.
        if progress > 0.0:
            progress = 2 ** (20 * progress - 10) / 2 if progress < 0.5 else (2 - 2 ** (10 - 20 * progress)) / 2
        colors = []
        for x, (old_body, old_eyes) in enumerate(origin):
            edge = min(x, GRID_W - 1 - x)
            center = GRID_W // 2 - 1
            column = center - edge if direction > 0 else edge
            amount = clamp((progress - 0.58 * column / center) / 0.42, 0.0, 1.0)
            amount = amount * amount * (3.0 - 2.0 * amount)
            colors.append((mix_color(old_body, target[0], amount), mix_color(old_eyes, target[1], amount)))
        return tuple(colors)

    def _render_frame(self, key, coat=None):
        eyes, look, arms, legs, squash, blush, mouth = key
        body_color, eye_color = self._palette()[1:]
        blush_color = mix_color(body_color, 0xFFFF4F79, 0.5)
        palette_indices = {body_color: 0, eye_color: 1, blush_color: 2} if coat is not None else None
        s = self._unit
        bmp = Bitmap.createBitmap(GRID_W * s, GRID_H * s, Bitmap.Config.ARGB_8888)
        canvas = Canvas(bmp)
        paint = Paint()

        def px(x0, y0, x1, y1, color):
            if palette_indices is None:
                paint.setColor(jint(as_java_color(color)))
                canvas.drawRect(float(x0 * s), float(y0 * s), float(x1 * s), float(y1 * s), paint)
                return
            role = palette_indices[color]
            for x in range(max(0, x0), min(GRID_W, x1)):
                cell_color = coat[x][role] if role < 2 else mix_color(coat[x][0], 0xFFFF4F79, 0.5)
                paint.setColor(jint(as_java_color(cell_color)))
                canvas.drawRect(float(x * s), float(y0 * s), float((x + 1) * s), float(y1 * s), paint)

        left, right, top, bottom = 3, 15, 3, 10
        if squash > 0:
            left, right, top = 2, 16, 5
        elif squash < 0:
            left, right, top = 4, 14, 2
        if legs == "tuck":
            top += 1
            bottom += 1

        heights = {
            "stand": (2, 2, 2, 2),
            "walk1": (1, 2, 1, 2),
            "walk2": (2, 1, 2, 1),
            "dangle": (3, 3, 3, 3),
            "tuck": (1, 1, 1, 1),
        }.get(legs, (2, 2, 2, 2))
        for col, h in zip((left + 1, left + 3, right - 4, right - 2), heights):
            px(col, bottom, col + 1, bottom + h, body_color)

        def arm(side, style):
            if style == "up":
                if side < 0:
                    px(left - 2, top - 1, left - 1, top + 3, body_color)
                    px(left - 1, top + 2, left, top + 3, body_color)
                else:
                    px(right + 1, top - 1, right + 2, top + 3, body_color)
                    px(right, top + 2, right + 1, top + 3, body_color)
            elif style == "tuck":
                if side < 0:
                    px(left - 1, top + 3, left, top + 5, body_color)
                else:
                    px(right, top + 3, right + 1, top + 5, body_color)
            else:
                if side < 0:
                    px(left - 2, top + 3, left, top + 5, body_color)
                else:
                    px(right, top + 3, right + 2, top + 5, body_color)

        left_arm = right_arm = arms
        if arms == "wave1":
            left_arm, right_arm = "up", "normal"
        elif arms == "wave2":
            left_arm, right_arm = "normal", "up"
        arm(-1, left_arm)
        arm(1, right_arm)

        px(left, top, right, bottom, body_color)

        ey = top + 2
        for side, cx in ((-1, left + 3 + look), (1, right - 4 + look)):
            if eyes == "blink":
                px(cx, ey + 1, cx + 1, ey + 2, eye_color)
            elif eyes == "happy":
                px(cx - 1, ey + 1, cx, ey + 2, eye_color)
                px(cx, ey, cx + 1, ey + 1, eye_color)
                px(cx + 1, ey + 1, cx + 2, ey + 2, eye_color)
            elif eyes == "surprised":
                px(cx, ey - 1, cx + 1, ey + 2, eye_color)
            elif eyes == "dizzy":
                for dx, dy in ((-1, -1), (1, -1), (0, 0), (-1, 1), (1, 1)):
                    px(cx + dx, ey + dy, cx + dx + 1, ey + dy + 1, eye_color)
            elif eyes == "sleep":
                px(cx - 1, ey + 1, cx + 2, ey + 2, eye_color)
            elif eyes == "angry":
                px(cx, ey, cx + 1, ey + 2, eye_color)
                brow = cx - 1 if side < 0 else cx + 1
                px(brow, ey - 1, brow + 1, ey, eye_color)
            else:
                px(cx, ey, cx + 1, ey + 2, eye_color)
            if blush:
                if side < 0:
                    px(cx - 2, ey + 2, cx, ey + 3, blush_color)
                else:
                    px(cx + 1, ey + 2, cx + 3, ey + 3, blush_color)

        if mouth:
            mx = (left + right) // 2
            my = ey + 2
            if mouth == "open":
                px(mx - 1, my, mx + 1, max(my + 1, min(my + 2, bottom)), eye_color)
            else:
                px(mx - 1, my, mx + 1, my + 1, eye_color)
        return bmp


    def _particle_bitmap(self, kind):
        bmp = self._particle_bitmaps.get(kind)
        if bmp is not None:
            return bmp
        rows = PATTERNS[kind]
        if kind == "poop":
            colors = POOP_COLORS
            s = max(2, int(self._unit * 0.9))
        else:
            colors = {"X": {"heart": HEART_COLOR, "zzz": ZZZ_COLOR, "spark": SPARK_COLOR, "note": NOTE_COLOR}[kind]}
            s = max(2, int(self._unit * 0.75))
        bmp = Bitmap.createBitmap(len(rows[0]) * s, len(rows) * s, Bitmap.Config.ARGB_8888)
        canvas = Canvas(bmp)
        paints = {}
        for ch, color in colors.items():
            paint = Paint()
            paint.setColor(jint(as_java_color(color)))
            paints[ch] = paint
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                paint = paints.get(ch)
                if paint is not None:
                    canvas.drawRect(float(x * s), float(y * s), float((x + 1) * s), float((y + 1) * s), paint)
        self._particle_bitmaps[kind] = bmp
        return bmp


    def _reset_state(self):
        now = time.monotonic()
        self._state = "idle"
        self._x = 0.0
        self._y = 0.0
        self._vx = 0.0
        self._vy = 0.0
        self._pos_ready = False
        self._last_tick = now
        self._last_interaction = now
        self._next_action = now + 4.0
        self._next_blink = now + 2.0
        self._blink_until = 0.0
        self._look = 0
        self._expr = None
        self._expr_until = 0.0
        self._squash_until = 0.0
        self._wave_until = 0.0
        self._hop_active = False
        self._hop_off = 0.0
        self._hop_vy = 0.0
        self._walk_target = 0.0
        self._touch = None
        self._last_tap = 0.0
        self._recent_taps = []
        self._next_heart = 0.0
        self._next_z = 0.0
        self._bubble_visible = False
        self._bubble_fade_in = False
        self._bubble_until = 0.0
        self._bubble_gen = 0
        self._last_phrase = None
        self._bounds_cache = None
        self._bounds_time = 0.0
        self._greet_pending = False
        self._mouth_open_until = 0.0
        self._chew_until = 0.0
        self._media = None
        self._media_check = 0.0
        self._next_note = 0.0
        self._next_msg_react = 0.0
        self._rotation = 0
        self._accel_prev = None
        self._shake_hits = []
        self._shake_pending = 0.0
        self._shake_dizzy = False
        self._tilt = 0.0
        self._slide_v = 0.0
        self._next_slide_say = 0.0
        self._platform = None
        self._platforms = []
        self._platform_scan = 0.0
        self._next_phone_perch = 0.0
        self._platform_moving = False
        self._walk_in = False
        self._hang_start = 0.0
        self._hang_until = 0.0
        self._peek_side = 0
        self._peek_pending = 0
        self._peek_phase = "hide"
        self._peek_phase_until = 0.0
        self._peek_end = 0.0
        self._peek_moving = False
        self._poop_due = 0.0
        self._poop_need = random.randint(*POOP_EVERY)
        self._platform_span = None
        self._next_peek = now + PEEK_AFTER


    def _schedule(self, delay):
        if self._handler is None or self._tick_r is None:
            return
        self._handler.removeCallbacks(self._tick_r)
        if delay <= 16:
            self.host.scheduleFrame(self._tick_r)
        else:
            self.host.cancelFrame()
            self.host.idleFrames()
            self._handler.postDelayed(self._tick_r, int(delay))


    def _tick(self):
        if not self._running or self._paused:
            return
        try:
            self._step()
        except Exception as e:
            log(f"[{__id__}] tick error: {e}")
        if self._running and not self._paused:
            self._schedule(self._next_delay())


    def _next_delay(self):
        if self._pet is None:
            return 1000
        now = time.monotonic()
        if (
            self._state in ("press", "drag", "pet", "fly", "walk")
            or self._hop_active
            or self._coat_transition is not None
            or self.host.isBlurActive()
            or self._bubble_fade_in
            or self._walk_in
            or self._platform_moving
            or self._slide_v
            or self._shake_pending
            or now < self._squash_until
            or (self._expr == "dizzy" and now < self._expr_until)
        ):
            return 16
        if self._state in ("hang", "peek"):
            return 33
        if self._state == "sleep":
            return 250
        if self._media and self._state == "idle":
            return 33
        return 80


    def _dp(self, value):
        return float(self.host.dp(value))


    def _step(self):
        now = time.monotonic()
        dt = clamp(now - self._last_tick, 0.0, 0.05)
        self._last_tick = now

        if self._pet is None or self._decor is None:
            return
        if self._pos_ready:
            self._poll_media(now)
        min_x, max_x, min_y, max_y, width, height = self._bounds(now)
        if width <= 0 or height <= 0:
            return
        if not self._pos_ready:
            self._restore_position(min_x, max_x, min_y, max_y)
            self._pos_ready = True
            if self._greet_pending:
                self._greet_pending = False
                self._hop(self._dp(420))
                self._say("hello")

        gravity = self._gravity_enabled()
        state = self._state

        if state == "press" and self._touch is not None and now - self._touch["t0"] >= 0.4:
            self._start_pet(now)
            state = self._state

        if self._shake_pending and state not in ("press", "drag", "pet"):
            self._apply_shake(now, gravity)
            state = self._state

        if state == "pet":
            if now >= self._next_heart:
                self._next_heart = now + 0.45
                self._spawn_particle("heart")
                self._haptic(4)
        elif state == "fly":
            self._step_fly(dt, now, gravity, min_x, max_x, min_y, max_y)
        elif state == "hang":
            self._step_hang(now, gravity, min_x, max_x, min_y)
        elif state == "peek":
            self._step_peek(dt, now, width)
        elif state in ("idle", "walk", "sleep"):
            self._step_ground(dt, now, gravity, min_x, max_x, min_y, max_y)

        if self._hop_active:
            self._hop_off += self._hop_vy * dt
            self._hop_vy += self._dp(2600) * dt
            if self._hop_off >= 0.0:
                self._impact_effect(self._x + self._w / 2, self._y + self._h,
                                    0, -1, self._hop_vy, True)
                self._hop_off = 0.0
                self._hop_vy = 0.0
                self._hop_active = False
                self._squash_until = now + 0.1

        self._render(now)
        self._update_bubble(now, width, min_y)


    def _step_ground(self, dt, now, gravity, min_x, max_x, min_y, max_y):
        self._walk_in = False
        if self._state != "walk":
            if self._x < min_x - 0.5 or self._x > max_x + 0.5:
                step = self._dp(60) * dt
                if self._x < min_x:
                    self._x = min(min_x, self._x + step)
                    self._look = 1
                else:
                    self._x = max(max_x, self._x - step)
                    self._look = -1
                self._walk_in = True
            else:
                self._x = clamp(self._x, min_x, max_x)

        self._platform_moving = False
        if gravity:
            if self._platform is not None:
                if not self._follow_platform(min_y, max_y):
                    self._fall(self._dp(42) * self._look if self._state == "walk" else 0.0)
                    return
            elif not self._hop_active:
                resting = self._y >= max_y - 0.5
                if resting:
                    keyboard = self._platform_rect(0)
                    feet = self._platform_feet()
                    if (keyboard is not None and min_y + feet <= keyboard[2] <= max_y + feet
                            and self._feet_on(keyboard[0], keyboard[1])):
                        self._platform = 0
                        self._follow_platform(min_y, max_y)
                    else:
                        self._y = max_y
                        if self._state == "idle" and self._try_phone_perch(
                                now, min_x, max_x, min_y, max_y):
                            return
                else:
                    self._fall(0.0)
                    return
            else:
                self._y = min(self._y, max_y)
            if self._step_slide(dt, now, min_x, max_x):
                return
        else:
            self._y = clamp(self._y, min_y, max_y)
            self._slide_v = 0.0

        if self._state == "walk":
            direction = 1 if self._walk_target > self._x else -1
            self._look = direction
            self._x += direction * self._dp(42) * dt
            reached = (direction > 0 and self._x >= self._walk_target) or (direction < 0 and self._x <= self._walk_target)
            hit_edge = (direction < 0 and self._x <= min_x) or (direction > 0 and self._x >= max_x)
            if reached or hit_edge:
                self._x = clamp(self._x, min_x, max_x)
                self._state = "idle"
                self._next_action = now + random.uniform(2.5, 6.0)
                side = self._peek_pending
                self._peek_pending = 0
                edge = self._dp(4)
                if side and ((side < 0 and self._x <= min_x + edge) or (side > 0 and self._x >= max_x - edge)):
                    self._start_peek(now, side)
                    return
                self._save_position()
            return

        if self._state == "sleep":
            if now >= self._next_z:
                self._next_z = now + 1.6
                self._spawn_particle("zzz")
            return

        if now >= self._next_blink:
            self._blink_until = now + 0.13
            self._next_blink = now + (0.35 if random.random() < 0.15 else random.uniform(2.0, 5.5))

        sleep_idx = clamp(int(self._cfg.get("sleep", 1)), 0, len(SLEEP_SECONDS) - 1)
        timeout = SLEEP_SECONDS[sleep_idx]
        if (
            timeout
            and now - self._last_interaction > timeout
            and not self._hop_active
            and not self._media
            and not self._walk_in
        ):
            self._state = "sleep"
            self._look = 0
            self._next_z = now + 0.6
            self._hide_bubble()
            return

        if self._poop_due and now >= self._poop_due and not self._hop_active and not self._walk_in:
            self._do_poop(now, min_x, max_x)
            return

        if self._media:
            if now >= self._next_note:
                self._next_note = now + (0.8 if self._media == "music" else 1.5)
                self._spawn_particle("note")
            return

        if now < self._next_action or self._hop_active or self._walk_in:
            return
        self._next_action = now + random.uniform(3.5, 9.0)
        roll = random.random()
        if not self._cfg.get("wander", True):
            if roll < 0.5:
                self._look = random.choice((-1, 0, 1))
            return
        if (
            self._platform is None
            and now >= self._next_peek
            and now - self._last_interaction > PEEK_AFTER
            and roll < 0.3
        ):
            side = -1 if self._x - min_x < max_x - self._x else 1
            self._walk_target = min_x if side < 0 else max_x
            self._peek_pending = side
            self._state = "walk"
            return
        if roll < 0.38:
            distance = random.uniform(self._dp(40), self._dp(160)) * random.choice((-1, 1))
            lo, hi = min_x, max_x
            if self._platform is not None and self._platform_span is not None:
                x0, x1 = self._platform_span
                lo = max(lo, x0 - self._unit * 4)
                hi = min(hi, x1 - self._unit * 14)
            target = clamp(self._x + distance, lo, hi)
            if abs(target - self._x) > self._dp(12):
                self._walk_target = target
                self._state = "walk"
        elif roll < 0.58:
            self._look = random.choice((-1, 0, 1))
        elif roll < 0.72:
            self._say("idle")
        elif roll < 0.84:
            self._hop(self._dp(360))
        else:
            self._wave_until = now + 1.6


    def _fall(self, vx):
        self._state = "fly"
        self._platform = None
        self._platforms = []
        self._platform_scan = 0.0
        self._vx = vx + self._slide_v
        self._vy = 0.0
        self._slide_v = 0.0


    def _tilt_accel(self):
        tilt = self._tilt if self._sensor_listener is not None else 0.0
        if abs(tilt) <= TILT_DEAD:
            return 0.0
        return (tilt - math.copysign(TILT_DEAD, tilt)) * self._dp(2200)


    def _step_slide(self, dt, now, min_x, max_x):
        if not self._hop_active:
            self._slide_v += self._tilt_accel() * dt
        self._slide_v *= math.exp(-2.5 * dt)
        if abs(self._slide_v) < self._dp(6):
            self._slide_v = 0.0
            return False
        self._x += self._slide_v * dt
        if self._x < min_x or self._x > max_x:
            left = self._x < min_x
            self._x = clamp(self._x, min_x, max_x)
            self._impact_effect(self._x if left else self._x + self._w,
                                self._y + self._h / 2, 1 if left else -1, 0, abs(self._slide_v))
            if abs(self._slide_v) > self._dp(150):
                self._squash_until = now + 0.12
                self._haptic(3)
            self._slide_v = 0.0
            return False
        if abs(self._slide_v) < self._dp(60):
            return False
        if self._state == "sleep":
            self._set_expr("surprised", 0.8)
        self._state = "idle"
        self._peek_pending = 0
        self._look = 1 if self._slide_v > 0 else -1
        if now >= self._next_slide_say:
            self._next_slide_say = now + 8.0
            if random.random() < 0.5:
                self._say("slide")
        return True


    def _feet_on(self, x0, x1):
        feet0 = self._x + self._unit * 4
        feet1 = self._x + self._unit * 14
        return min(feet1, x1) - max(feet0, x0) >= self._unit * 2


    def _follow_platform(self, min_y, max_y):
        rect = self._platform_rect(self._platform)
        if rect is None:
            return False
        x0, x1, top = rect
        if self._platform >= 6 and self._platform_span is not None:
            prior_left, prior_right = self._platform_span
            prior_top = self._y + self._platform_feet()
            if (abs(top - prior_top) > self._dp(48)
                    or min(x1, prior_right) - max(x0, prior_left) < self._unit * 2):
                return False
        if not self._feet_on(x0, x1):
            return False
        y = top - self._platform_feet()
        if y < min_y - self._unit * 2 or y > max_y:
            return False
        self._platform_moving = abs(y - self._y) > 0.5
        self._platform_span = (x0, x1)
        self._y = y
        return True


    def _platform_landing(self, now, old_y, min_y, max_y):
        if now >= self._platform_scan:
            self._platform_scan = now + 0.10
            self._platforms = self._scan_platforms(min_y, max_y)
        feet = self._platform_feet()
        prev_bottom = old_y + feet
        new_bottom = self._y + feet
        best = None
        for item in self._platforms:
            top = item[3]
            if prev_bottom <= top + self._dp(2) and new_bottom >= top and self._feet_on(item[1], item[2]):
                if best is None or top < best[3]:
                    best = item
        if best is None:
            return None
        index, x0, x1, top = best
        actual = self._platform_rect(index)
        if (actual is None or not prev_bottom <= actual[2] + self._dp(2)
                or new_bottom < actual[2] or not self._feet_on(actual[0], actual[1])):
            self._platform_scan = 0.0
            return None
        x0, x1, top = actual
        speed = self._vy
        self._impact_effect(self._x + self._w / 2, top, 0, -1, speed, True)
        self._y = top - feet
        if speed > self._dp(380):
            self._vy = -speed * 0.42
            self._vx *= 0.75
            self._squash_until = now + 0.1
        else:
            self._platform_span = (x0, x1)
            self._land(now, index)
        return speed


    def _step_fly(self, dt, now, gravity, min_x, max_x, min_y, max_y):
        old_y = self._y
        self._x += self._vx * dt
        self._y += self._vy * dt
        if gravity:
            self._vy += self._dp(2600) * dt
            self._vx *= math.exp(-0.35 * dt)
            self._vx += self._tilt_accel() * dt
        else:
            friction = math.exp(-2.0 * dt)
            self._vx *= friction
            self._vy *= friction

        impact = 0.0
        if self._x < min_x:
            self._x = min_x
            impact = max(impact, abs(self._vx))
            self._impact_effect(min_x, self._y + self._h / 2, 1, 0, abs(self._vx))
            self._vx = abs(self._vx) * 0.55
        elif self._x > max_x:
            self._x = max_x
            impact = max(impact, abs(self._vx))
            self._impact_effect(max_x + self._w, self._y + self._h / 2, -1, 0, abs(self._vx))
            self._vx = -abs(self._vx) * 0.55

        landed = None
        if gravity and self._vy > 0 and self._y > old_y:
            landed = self._platform_landing(now, old_y, min_y, max_y)
        if landed is not None:
            impact = max(impact, landed)
        elif self._y < min_y:
            self._impact_effect(self._x + self._w / 2, min_y, 0, 1, abs(self._vy))
            if self._vy < -self._dp(250) and not self._shake_dizzy:
                self._start_hang(now, min_y)
                return
            self._y = min_y
            impact = max(impact, abs(self._vy))
            self._vy = abs(self._vy) * 0.55
        elif self._y > max_y:
            self._y = max_y
            impact = max(impact, abs(self._vy))
            self._impact_effect(self._x + self._w / 2, max_y + self._h, 0, -1, abs(self._vy), True)
            if gravity and self._vy < self._dp(380):
                self._land(now)
            else:
                self._vy = -abs(self._vy) * (0.42 if gravity else 0.55)
                self._vx *= 0.75 if gravity else 1.0
                self._squash_until = now + 0.1

        if impact > self._dp(2300):
            self._set_expr("dizzy", 2.6)
            self._say("dizzy")
            self._haptic(1)
        elif impact > self._dp(900):
            self._haptic(3)

        if self._state == "fly" and not gravity and math.hypot(self._vx, self._vy) < self._dp(45):
            self._land(now)


    def _land(self, now, platform=None):
        self._state = "idle"
        self._vx = 0.0
        self._vy = 0.0
        self._platform = platform
        self._platforms = []
        self._squash_until = now + 0.14
        self._next_action = now + random.uniform(2.0, 5.0)
        if self._shake_dizzy:
            self._shake_dizzy = False
            self._set_expr("dizzy", 3.0)
            self._say("dizzy")
            if self._bounds_cache is not None:
                min_x, max_x = self._bounds_cache[:2]
                distance = random.uniform(self._dp(30), self._dp(70)) * random.choice((-1, 1))
                self._walk_target = clamp(self._x + distance, min_x, max_x)
                if abs(self._walk_target - self._x) > self._dp(12):
                    self._state = "walk"
        self._save_position()


    def _on_accel(self, ax, ay, az):
        if not self._running or self._paused or self._pet is None:
            return
        now = time.monotonic()
        prev = self._accel_prev
        self._accel_prev = (ax, ay, az)
        if self._cfg["shake"] and prev is not None and self._state not in ("press", "drag", "pet"):
            jerk = math.sqrt((ax - prev[0]) ** 2 + (ay - prev[1]) ** 2 + (az - prev[2]) ** 2)
            if jerk > SHAKE_JERK:
                hits = [t for t in self._shake_hits if now - t < SHAKE_WINDOW]
                hits.append(now)
                if len(hits) >= SHAKE_HITS:
                    hits = []
                    self._shake_pending = max(self._shake_pending, jerk)
                    self._schedule(0)
                self._shake_hits = hits
        rotation = self._rotation
        if rotation == Surface.ROTATION_90:
            sx, sy = ay, ax
        elif rotation == Surface.ROTATION_180:
            sx, sy = ax, -ay
        elif rotation == Surface.ROTATION_270:
            sx, sy = -ay, -ax
        else:
            sx, sy = -ax, ay
        raw = clamp(sx / EARTH_G, -1.0, 1.0) if sy > 2.0 else 0.0
        self._tilt = self._tilt * 0.7 + raw * 0.3


    def _apply_shake(self, now, gravity):
        power = min(self._shake_pending, 40.0)
        self._shake_pending = 0.0
        was_sleeping = self._state == "sleep"
        if self._state == "peek":
            self._peek_side = 0
        self._peek_pending = 0
        self._platform = None
        self._platforms = []
        self._platform_scan = 0.0
        self._hop_active = False
        self._hop_off = 0.0
        self._hop_vy = 0.0
        self._slide_v = 0.0
        self._state = "fly"
        speed = self._dp(900 + power * 55)
        angle = random.uniform(0.0, math.pi * 2.0)
        self._vx = math.cos(angle) * speed
        self._vy = math.sin(angle) * speed - (self._dp(700) if gravity else 0.0)
        self._shake_dizzy = True
        self._last_interaction = now
        self._set_expr("surprised", 1.0)
        self._haptic(1)
        if was_sleeping:
            self._say("wake")
        elif random.random() < 0.6:
            self._say("shake")


    def _start_hang(self, now, min_y):
        self._state = "hang"
        self._vx = 0.0
        self._vy = 0.0
        self._platform = None
        self._y = min_y - self._unit * 2
        self._hang_start = now
        self._hang_until = now + random.uniform(2.0, 3.5)
        self._haptic(3)
        if random.random() < 0.7:
            self._say("hang")


    def _step_hang(self, now, gravity, min_x, max_x, min_y):
        self._x = clamp(self._x, min_x, max_x)
        self._y = min_y - self._unit * 2
        if now < self._hang_until:
            return
        self._state = "fly"
        self._vx = 0.0
        self._vy = self._dp(150) if gravity else self._dp(700)
        self._set_expr("surprised", 0.8)
        self._haptic(3)
        if random.random() < 0.6:
            self._say("hang_fall")


    def _peek_x(self, width, units):
        if self._peek_side > 0:
            return float(width - self.host.getInsetRight() - units * self._unit)
        return float(self.host.getInsetLeft() + units * self._unit - self._w)


    def _start_peek(self, now, side):
        self._state = "peek"
        self._peek_side = side
        self._peek_phase = "hide"
        self._peek_phase_until = now + random.uniform(3.0, 6.0)
        self._peek_end = now + random.uniform(30.0, 60.0)
        self._look = side


    def _step_peek(self, dt, now, width):
        phase = self._peek_phase
        target = self._peek_x(width, PEEK_OUT_UNITS if phase == "out" else PEEK_HIDDEN_UNITS)
        step = self._dp(50) * dt
        self._peek_moving = abs(target - self._x) > 0.5
        if self._peek_moving:
            self._x = min(target, self._x + step) if target > self._x else max(target, self._x - step)
        if phase == "hide" and self._peek_moving:
            self._look = self._peek_side
        else:
            self._look = -self._peek_side
        if now >= self._next_blink:
            self._blink_until = now + 0.13
            self._next_blink = now + random.uniform(2.0, 5.5)
        if now < self._peek_phase_until or self._peek_moving:
            return
        if now >= self._peek_end:
            self._end_peek(now)
            return
        if phase == "hide":
            self._peek_phase = "out"
            self._peek_phase_until = now + random.uniform(1.5, 2.8)
            if random.random() < 0.5:
                self._say("peek")
        else:
            self._peek_phase = "hide"
            self._peek_phase_until = now + random.uniform(3.0, 7.0)


    def _end_peek(self, now):
        side = self._peek_side
        self._peek_side = 0
        self._peek_moving = False
        self._next_peek = now + random.uniform(60.0, 120.0)
        self._state = "idle"
        if self._bounds_cache is None or not side:
            return
        min_x, max_x = self._bounds_cache[:2]
        inside = min_x + self._dp(30) if side < 0 else max_x - self._dp(30)
        self._walk_target = clamp(inside, min_x, max_x)
        self._state = "walk"
        self._next_action = now + random.uniform(3.0, 6.0)


    def _compose(self, now):
        eyes = "open"
        look = self._look
        arms = "normal"
        legs = "stand"
        squash = 0
        blush = False
        mouth = None
        state = self._state

        if state in ("drag", "fly"):
            eyes, arms, legs = "surprised", "up", "dangle"
        elif state == "pet":
            eyes, blush = "happy", True
            squash = 1 if int(now * 3) % 2 else 0
            look = 0
        elif state == "sleep":
            eyes, arms, legs, look = "sleep", "tuck", "tuck", 0
        elif state == "walk":
            legs = "walk1" if int(now * 7) % 2 else "walk2"
        elif state == "press":
            squash = 1
        elif state == "hang":
            eyes = "surprised" if now - self._hang_start < 0.5 else "open"
            arms, legs = "up", "dangle"
            look = -1 if int(now * 1.5) % 2 else 1
        elif state == "peek":
            if self._peek_moving:
                legs = "walk1" if int(now * 7) % 2 else "walk2"
            elif self._peek_phase == "out" and int(now * 3) % 2:
                arms = "wave1" if self._peek_side > 0 else "wave2"

        if self._walk_in and state == "idle":
            legs = "walk1" if int(now * 7) % 2 else "walk2"

        if state == "idle" and self._media and not self._hop_active:
            beat = int(now * 4) % 2
            eyes = "happy"
            if self._media == "music":
                arms = "wave1" if beat else "wave2"
                legs = "walk1" if beat else "walk2"
                squash = 1 if beat else 0
                look = -1 if int(now * 2) % 2 else 1
            else:
                look = 0

        if state in ("idle", "walk") and now < self._wave_until:
            arms = "wave1" if int(now * 5) % 2 else "wave2"

        if state in ("idle", "walk") and abs(self._slide_v) > self._dp(60):
            eyes, arms, legs = "surprised", "up", "stand"

        if self._hop_active and state in ("idle", "walk"):
            legs = "dangle"
            if self._hop_vy < 0:
                squash = -1
                arms = "up"

        if now < self._expr_until and self._expr and state != "sleep":
            eyes = self._expr
            if self._expr == "happy":
                blush = True
        if now < self._squash_until and squash == 0:
            squash = 1
        if eyes == "open" and state in ("idle", "walk", "peek", "hang") and now < self._blink_until:
            eyes = "blink"
        if legs == "tuck" and squash:
            squash = 0
        if state != "sleep":
            if now < self._mouth_open_until:
                mouth = "open"
            elif now < self._chew_until and int(now * 6) % 2:
                mouth = "chew"
        return (eyes, look, arms, legs, squash, blush, mouth)


    def _render(self, now):
        key = self._compose(now)
        coat = self._current_coat(now) if self._coat_transition is not None else None
        if self._coat_transition is None:
            coat = None
        frame_key = (key, coat)
        if frame_key != self._frame_key:
            self._frame_key = frame_key
            bitmap = self._get_frame(key) if coat is None else self._render_frame(key, coat)
            self._pet.setImageBitmap(bitmap)

        rotation = 0.0
        if self._state == "fly":
            rotation = clamp(self._vx / self._dp(70), -25.0, 25.0)
        elif self._state == "hang":
            rotation = math.sin((now - self._hang_start) * 3.0) * 6.0
        elif self._slide_v:
            rotation = clamp(-self._slide_v / self._dp(30), -12.0, 12.0)
        elif self._expr == "dizzy" and now < self._expr_until:
            rotation = math.sin(now * 10.0) * 7.0
        elif self._media and self._state == "idle" and not self._hop_active:
            if self._media == "music":
                rotation = math.sin(now * math.pi * 2.0) * 6.0
            else:
                rotation = math.sin(now * 2.5) * 3.0
        elif self._state == "drag" and self._touch is not None:
            if now - self._touch["hist"][-1][0] < 0.08:
                rotation = clamp(self._touch.get("vx", 0.0) / self._dp(90), -18.0, 18.0)
        y = float(self._y + self._hop_off)
        self.host.placeSprite(self._pet, float(self._x), y, float(rotation))
        self.host.sampleEffects(float(self._x), y, float(self._w), float(self._h), float(rotation),
                                jint(as_java_color(self._palette()[1])))


    def _restore_position(self, min_x, max_x, min_y, max_y):
        fx = self.get_setting("pos_x", None)
        fy = self.get_setting("pos_y", None)
        try:
            fx = float(fx) / 1000.0
            fy = float(fy) / 1000.0
            if fx < 0 or fy < 0:
                raise ValueError("No saved position")
        except Exception:
            fx, fy = 0.8, 0.72
        self._x = min_x + (max_x - min_x) * clamp(fx, 0.0, 1.0)
        self._y = min_y + (max_y - min_y) * clamp(fy, 0.0, 1.0)


    def _save_position(self):
        if self._bounds_cache is None:
            return
        min_x, max_x, min_y, max_y = self._bounds_cache[:4]
        fx = (self._x - min_x) / max(1.0, max_x - min_x)
        fy = (self._y - min_y) / max(1.0, max_y - min_y)
        try:
            self.set_setting("pos_x", int(clamp(fx, 0.0, 1.0) * 1000))
            self.set_setting("pos_y", int(clamp(fy, 0.0, 1.0) * 1000))
        except Exception as e:
            log(f"[{__id__}] save position error: {e}")


    def _recenter(self):
        if self._decor is None or self._decor.getWidth() <= 0:
            return
        now = time.monotonic()
        min_x, max_x, min_y, max_y = self._bounds(now)[:4]
        self._x = (min_x + max_x) / 2.0
        self._y = (min_y + max_y) / 2.0
        self._pos_ready = True
        self._state = "idle"
        self._vx = self._vy = 0.0
        self._slide_v = 0.0
        self._platform = None
        self._peek_side = 0
        self._peek_pending = 0
        self._last_interaction = now
        self._hop(self._dp(500))
        for _ in range(3):
            self._spawn_particle("spark")
        self._say("hello")
        self._save_position()
        self._schedule(16)


    def _on_touch(self, event):
        action = event.getActionMasked()
        rx = float(event.getRawX())
        ry = float(event.getRawY())
        now = time.monotonic()

        if action == MotionEvent.ACTION_DOWN:
            was_sleeping = self._state == "sleep"
            was_peeking = self._state == "peek"
            self._last_interaction = now
            self._touch = {
                "x0": rx, "y0": ry, "t0": now,
                "ox": rx - self._x, "oy": ry - self._y,
                "hist": [(now, rx, ry)],
                "anchor": rx, "dir": 0, "vx": 0.0,
            }
            self._hop_active = False
            self._hop_off = 0.0
            self._hop_vy = 0.0
            self._vx = self._vy = 0.0
            self._slide_v = 0.0
            self._peek_side = 0
            self._peek_pending = 0
            self._state = "press"
            if was_sleeping:
                self._set_expr("surprised", 0.9)
                self._say("wake")
            elif was_peeking:
                self._next_peek = now + random.uniform(60.0, 120.0)
                self._set_expr("surprised", 0.7)
                self._say("found")
            self._schedule(0)
            return True

        touch = self._touch
        if touch is None:
            return False

        if action == MotionEvent.ACTION_MOVE:
            hist = touch["hist"]
            hist.append((now, rx, ry))
            while len(hist) > 2 and now - hist[0][0] > 0.1:
                hist.pop(0)
            if self._state == "press":
                if math.hypot(rx - touch["x0"], ry - touch["y0"]) > self._dp(8):
                    self._state = "drag"
                    self._platform = None
                    self._haptic(3)
                    if random.random() < 0.25:
                        self._say("drag")
            if self._state == "drag":
                width = self._decor.getWidth() if self._decor is not None else 0
                height = self._decor.getHeight() if self._decor is not None else 0
                self._x = clamp(rx - touch["ox"], -self._w * 0.3, width - self._w * 0.7)
                self._y = clamp(ry - touch["oy"], 0.0, height - self._h * 0.5)
                t0, x0, _ = hist[0]
                if now - t0 > 0.005:
                    touch["vx"] = (rx - x0) / (now - t0)
            elif self._state == "pet":
                dx = rx - touch["anchor"]
                if abs(dx) > self._dp(10):
                    direction = 1 if dx > 0 else -1
                    if touch["dir"] and direction != touch["dir"]:
                        self._stroke()
                    touch["dir"] = direction
                    touch["anchor"] = rx
            return True

        if action in (MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL):
            self._touch = None
            self._last_interaction = now
            state = self._state
            if state == "drag":
                self._release_drag(now, touch, action == MotionEvent.ACTION_CANCEL)
            elif state == "pet":
                self._end_pet(now)
            elif state == "press":
                self._state = "idle"
                if action == MotionEvent.ACTION_UP:
                    self._tap(now)
            self._schedule(0)
            return True
        return True


    def _release_drag(self, now, touch, cancelled):
        vx = vy = 0.0
        hist = touch["hist"]
        if not cancelled and len(hist) >= 2:
            t0, x0, y0 = hist[0]
            t1, x1, y1 = hist[-1]
            if t1 - t0 > 0.005 and now - t1 < 0.08:
                vx = (x1 - x0) / (t1 - t0)
                vy = (y1 - y0) / (t1 - t0)
        limit = self._dp(6000)
        vx = clamp(vx, -limit, limit)
        vy = clamp(vy, -limit, limit)
        speed = math.hypot(vx, vy)
        if not cancelled and speed <= self._dp(700):
            self.host.rememberDrop(float(self._x + self._w / 2), float(self._y + self._platform_feet()))
        min_y = self._bounds_cache[2] if self._bounds_cache is not None else None
        if (not cancelled and speed <= self._dp(700) and min_y is not None
                and self._phone_geometry_available()
                and self._grab_phone_ledge(now, min_y, self._bounds_cache[3])):
            return
        if speed <= self._dp(700) and min_y is not None and self._y <= min_y + self._unit * 2:
            self._start_hang(now, min_y)
        elif speed > self._dp(700) or self._gravity_enabled():
            self._state = "fly"
            self._vx = vx
            self._vy = vy
            if speed > self._dp(2600):
                self._say("throw")
        else:
            self._state = "idle"
            self._squash_until = now + 0.12
            self._next_action = now + random.uniform(2.0, 5.0)
            self._save_position()


    def _tap(self, now):
        if now - self._last_tap < 0.32:
            self._last_tap = 0.0
            self._hop(self._dp(720))
            self._set_expr("happy", 1.3)
            for _ in range(3):
                self._spawn_particle("spark")
            self._haptic(0)
            if random.random() < 0.4:
                self._say("jump")
            return
        self._last_tap = now
        self._recent_taps = [t for t in self._recent_taps if now - t < 2.5] + [now]
        if len(self._recent_taps) >= 6:
            self._recent_taps = []
            self._set_expr("angry", 2.5)
            self._say("annoyed")
            self._haptic(1)
            return
        self._squash_until = now + 0.12
        self._set_expr("surprised", 0.45)
        self._hop(self._dp(330))
        self._haptic(3)
        if random.random() < 0.35:
            self._say("tap")


    def _start_pet(self, now):
        self._state = "pet"
        self._next_heart = now + 0.1
        self._haptic(0)


    def _stroke(self):
        self._spawn_particle("heart")
        self._haptic(4)


    def _end_pet(self, now):
        self._state = "idle"
        self._set_expr("happy", 2.2)
        self._next_action = now + random.uniform(3.0, 6.0)
        try:
            count = int(self.get_setting("pets_count", 0) or 0) + 1
            self.set_setting("pets_count", count)
        except Exception:
            pass
        if random.random() < 0.6:
            self._say("pet")


    def _can_react(self):
        return (
            self._running
            and not self._paused
            and self._pet is not None
            and self._pos_ready
            and self._cfg.get("tg_events", True)
            and self._state in ("idle", "walk", "sleep", "peek")
        )


    def _wake_for_event(self, now):
        self._last_interaction = now
        if self._state == "peek":
            self._end_peek(now)
        elif self._state == "sleep":
            self._state = "idle"
            self._set_expr("surprised", 0.9)


    def _match_keyword(self, texts):
        for mood, pattern in KEYWORDS:
            for text in texts:
                if pattern.search(text):
                    return mood
        return None


    def _react_incoming(self, mood, in_chat):
        now = time.monotonic()
        if mood is None and in_chat:
            return
        if now < self._next_msg_react:
            return
        self._next_msg_react = now + (3.0 if mood else 6.0)
        self._wake_for_event(now)
        if mood == "love":
            self._set_expr("happy", 2.2)
            for _ in range(3):
                self._spawn_particle("heart")
            self._say("love")
        elif mood == "laugh":
            self._set_expr("happy", 1.6)
            self._hop(self._dp(420))
            self._spawn_particle("spark")
            self._say("laugh")
        elif mood == "hi":
            self._wave_until = now + 1.8
            self._say("hi")
        else:
            self._look = random.choice((-1, 1))
            self._set_expr("surprised", 0.7)
            self._hop(self._dp(300))
            self._say("mail")
        self._next_action = max(self._next_action, now + 2.5)
        self._schedule(0)


    def _set_expr(self, expr, duration):
        self._expr = expr
        self._expr_until = time.monotonic() + duration


    def _hop(self, strength):
        if self._state not in ("idle", "walk"):
            return
        self._hop_active = True
        self._hop_vy = -float(strength)
        if self._hop_off > 0:
            self._hop_off = 0.0


    def _haptic(self, kind):
        if not self._cfg.get("haptics", True) or self._pet is None:
            return
        try:
            self._pet.performHapticFeedback(kind)
        except Exception:
            pass


    def _spawn_particle(self, kind, origin=None):
        if self._decor is None or self._pet is None or len(self._particles) >= MAX_PARTICLES:
            return
        try:
            bmp = self._particle_bitmap(kind)
            bw, bh = bmp.getWidth(), bmp.getHeight()
            view = ImageView(self._pet.getContext())
            view.setImageBitmap(bmp)
            self._decor.addView(view, FrameLayout.LayoutParams(bw, bh))
            self._particles.append(view)

            head_y = self._y + self._hop_off + self._unit * 2 - bh
            if origin is not None:
                sx = origin[0] - bw / 2.0 + random.uniform(-self._dp(8), self._dp(8))
                sy = origin[1] - bh
                drift = random.uniform(-self._dp(16), self._dp(16))
            elif kind == "zzz":
                sx = self._x + self._w * 0.72
                sy = head_y + self._unit * 2
                drift = self._dp(18)
            else:
                sx = self._x + self._w / 2.0 - bw / 2.0 + random.uniform(-self._w * 0.25, self._w * 0.25)
                sy = head_y
                drift = random.uniform(-self._dp(16), self._dp(16))
            view.setTranslationX(float(sx))
            view.setTranslationY(float(sy))
            view.setScaleX(0.5)
            view.setScaleY(0.5)
            view.setRotation(float(random.uniform(-15, 15)))

            anim = view.animate()
            anim.translationXBy(float(drift))
            anim.translationYBy(float(-self._dp(random.uniform(40, 58))))
            anim.scaleX(1.0).scaleY(1.0).alpha(0.0)
            anim.setDuration(1100 if kind != "zzz" else 1600)
            anim.withEndAction(R(lambda v=view: self._remove_particle(v)))
            anim.start()
        except Exception as e:
            log(f"[{__id__}] particle error: {e}")


    def _remove_particle(self, view):
        try:
            self._particles.remove(view)
        except ValueError:
            pass
        self._remove_view(view)


    def _say(self, category, force=False, name=None):
        if self._bubble is None or (not force and not self._cfg.get("phrases", True)):
            return
        phrases = PHRASES.get(category, [category])
        if name is None:
            phrases = [p for p in phrases if "{name}" not in p] or phrases
        options = [p for p in phrases if p != self._last_phrase] or phrases
        text = random.choice(options)
        if "{name}" in text:
            text = text.replace("{name}", name or "")
        self._last_phrase = text
        now = time.monotonic()
        try:
            bg = GradientDrawable()
            bg.setCornerRadius(float(self.host.dp(12)))
            bg.setColor(self.host.getBubbleColor())
            bg.setStroke(max(1, self.host.dp(1)), self.host.getBubbleStroke())
            self._bubble.setBackground(bg)
            self._bubble.setTextColor(self.host.getBubbleText())
            try:
                self._bubble.setElevation(float(self.host.dp(2)))
            except Exception:
                pass
            self._bubble.animate().cancel()
            self._bubble.setText(text)
            self._bubble.setAlpha(0.0)
            self._bubble.setScaleX(0.84)
            self._bubble.setScaleY(0.80)
            self._bubble.setVisibility(View.VISIBLE)
            self._bubble_gen += 1
            self._bubble_visible = True
            self._bubble_fade_in = True
            self._bubble_until = now + 2.0 + len(text) * 0.05
        except Exception as e:
            log(f"[{__id__}] bubble error: {e}")


    def _hide_bubble(self):
        if self._bubble is None or not self._bubble_visible:
            return
        self._bubble_visible = False
        self._bubble_fade_in = False
        self._bubble_gen += 1
        gen = self._bubble_gen
        bubble = self._bubble

        def _gone():
            if gen == self._bubble_gen and bubble is not None:
                bubble.setVisibility(View.GONE)

        anim = bubble.animate().alpha(0.0).scaleX(0.91).scaleY(0.89).setDuration(220)
        anim.setInterpolator(PathInterpolator(0.30, 0.0, 0.74, 0.44))
        anim.withEndAction(R(_gone))
        anim.start()


    def _update_bubble(self, now, width, min_y):
        if self._bubble is None or not self._bubble_visible:
            return
        if now >= self._bubble_until:
            self._hide_bubble()
            return
        bw = self._bubble.getWidth()
        bh = self._bubble.getHeight()
        if bw <= 0:
            return
        margin = self._dp(4)
        bx = clamp(self._x + self._w / 2.0 - bw / 2.0, margin, width - bw - margin)
        by = self._y + self._hop_off + self._unit - bh - margin
        if by < min_y:
            by = self._y + self._hop_off + self._h + margin
        self._bubble.setTranslationX(float(bx))
        self._bubble.setTranslationY(float(by))
        if self._bubble_fade_in:
            self._bubble_fade_in = False
            animation = self._bubble.animate().alpha(1.0).scaleX(1.0).scaleY(1.0).setDuration(350)
            animation.setInterpolator(PathInterpolator(0.18, 0.66, 0.23, 1.0))
            animation.start()


    def _do_poop(self, now, min_x, max_x):
        self._poop_due = 0.0
        if not self._cfg.get("poop", True) or self._decor is None or self._pet is None:
            return
        if len(self._poops) >= MAX_POOPS:
            return
        side = -self._look if self._look else random.choice((-1, 1))
        try:
            bmp = self._particle_bitmap("poop")
            bw, bh = bmp.getWidth(), bmp.getHeight()
            pad = self.host.dp(6)
            view = ImageView(self._pet.getContext())
            view.setImageBitmap(bmp)
            view.setScaleType(ImageView.ScaleType.CENTER)
            view.setPadding(pad, pad, pad, pad)
            if self._poop_listener is None:
                self._poop_listener = PoopClickListener(self)
            view.setOnClickListener(self._poop_listener)
            feet = self._y + self._unit * 12
            px = self._x + self._w / 2.0 + side * self._w * 0.42 - bw / 2.0 - pad
            px = clamp(px, 0.0, float(self._decor.getWidth() - bw - pad * 2))
            py = clamp(feet - bh - pad, self.host.getInsetTop(), self._decor.getHeight() - bh - pad * 2)
            view.setContentDescription("Убрать кучку")
            self.host.addSprite(view, bw + pad * 2, bh + pad * 2)
            self.host.placeSprite(view, float(px), float(py), 0.0)
            self._poop_origins.append((view, px, py))
            view.setScaleX(0.1)
            view.setScaleY(0.1)
            anim = view.animate().scaleX(1.0).scaleY(1.0)
            anim.setInterpolator(OvershootInterpolator(2.0))
            anim.setDuration(320)
            anim.start()
            self._poops.append(view)
        except Exception as e:
            log(f"[{__id__}] poop error: {e}")
            return
        self._squash_until = now + 0.2
        self._set_expr("happy", 1.8)
        self._haptic(3)
        self._say("poop")
        self._look = -side
        target = clamp(self._x - side * random.uniform(self._dp(40), self._dp(80)), min_x, max_x)
        if abs(target - self._x) > self._dp(12):
            self._walk_target = target
            self._state = "walk"
        self._next_action = now + random.uniform(3.0, 6.0)


    def _clean_poop(self, view):
        target = None
        for poop in self._poops:
            if poop.equals(view):
                target = poop
                break
        if target is None:
            return
        self._poops.remove(target)
        target.setClickable(False)
        x, y = next(((x, y) for item, x, y in self._poop_origins if item.equals(target)), (self._x, self._y))
        self._poop_origins = [(item, x, y) for item, x, y in self._poop_origins if not item.equals(target)]
        cx = x + target.getWidth() / 2.0
        cy = y + target.getHeight() / 2.0
        for _ in range(3):
            self._spawn_particle("spark", origin=(cx, cy))
        anim = target.animate().scaleX(0.0).scaleY(0.0).alpha(0.0).rotation(90.0).setDuration(260)
        anim.withEndAction(R(lambda v=target: self._remove_view(v)))
        anim.start()
        try:
            self.set_setting("poops_cleaned", int(self.get_setting("poops_cleaned", 0) or 0) + 1)
        except Exception:
            pass
        self._haptic(3)
        if self._pet is not None and self._state in ("idle", "walk"):
            self._set_expr("happy", 1.5)
            if random.random() < 0.7:
                self._say("clean")
            self._schedule(0)


    def _remove_poops(self):
        poops = self._poops
        self._poops = []
        self._poop_origins = []
        for view in poops:
            self._remove_view(view)


    def _remove_view(self, view):
        if view is None:
            return
        try:
            view.animate().cancel()
        except Exception:
            pass
        if self.host.removeSprite(view):
            return
        try:
            parent = view.getParent()
            if parent is not None:
                parent.removeView(view)
        except Exception:
            pass

