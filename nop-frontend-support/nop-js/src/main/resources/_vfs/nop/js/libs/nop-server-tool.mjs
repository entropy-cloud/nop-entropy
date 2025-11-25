/*
  @license
	Rollup.js v3.29.4
	Thu, 28 Sep 2023 04:54:30 GMT - commit 4e92d60fa90cead39481e3703d26e5d812f43bd1

	https://github.com/rollup/rollup

	Released under the MIT License.
*/
var e = "3.29.4";
function t(e4) {
  return e4 && e4.__esModule && Object.prototype.hasOwnProperty.call(e4, "default") ? e4.default : e4;
}
var s = { exports: {} };
!function(e4) {
  const t2 = ",".charCodeAt(0), s2 = ";".charCodeAt(0), i2 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/", n2 = new Uint8Array(64), r2 = new Uint8Array(128);
  for (let e5 = 0; e5 < i2.length; e5++) {
    const t3 = i2.charCodeAt(e5);
    n2[e5] = t3, r2[t3] = e5;
  }
  const o2 = "undefined" != typeof TextDecoder ? new TextDecoder() : "undefined" != typeof Buffer ? { decode: (e5) => Buffer.from(e5.buffer, e5.byteOffset, e5.byteLength).toString() } : { decode(e5) {
    let t3 = "";
    for (let s3 = 0; s3 < e5.length; s3++)
      t3 += String.fromCharCode(e5[s3]);
    return t3;
  } };
  function a2(e5) {
    const t3 = new Int32Array(5), s3 = [];
    let i3 = 0;
    do {
      const n3 = l2(e5, i3), r3 = [];
      let o3 = true, a3 = 0;
      t3[0] = 0;
      for (let s4 = i3; s4 < n3; s4++) {
        let i4;
        s4 = c2(e5, s4, t3, 0);
        const l3 = t3[0];
        l3 < a3 && (o3 = false), a3 = l3, h2(e5, s4, n3) ? (s4 = c2(e5, s4, t3, 1), s4 = c2(e5, s4, t3, 2), s4 = c2(e5, s4, t3, 3), h2(e5, s4, n3) ? (s4 = c2(e5, s4, t3, 4), i4 = [l3, t3[1], t3[2], t3[3], t3[4]]) : i4 = [l3, t3[1], t3[2], t3[3]]) : i4 = [l3], r3.push(i4);
      }
      o3 || u2(r3), s3.push(r3), i3 = n3 + 1;
    } while (i3 <= e5.length);
    return s3;
  }
  function l2(e5, t3) {
    const s3 = e5.indexOf(";", t3);
    return -1 === s3 ? e5.length : s3;
  }
  function c2(e5, t3, s3, i3) {
    let n3 = 0, o3 = 0, a3 = 0;
    do {
      const s4 = e5.charCodeAt(t3++);
      a3 = r2[s4], n3 |= (31 & a3) << o3, o3 += 5;
    } while (32 & a3);
    const l3 = 1 & n3;
    return n3 >>>= 1, l3 && (n3 = -2147483648 | -n3), s3[i3] += n3, t3;
  }
  function h2(e5, s3, i3) {
    return !(s3 >= i3) && e5.charCodeAt(s3) !== t2;
  }
  function u2(e5) {
    e5.sort(d2);
  }
  function d2(e5, t3) {
    return e5[0] - t3[0];
  }
  function p2(e5) {
    const i3 = new Int32Array(5), n3 = 16384, r3 = n3 - 36, a3 = new Uint8Array(n3), l3 = a3.subarray(0, r3);
    let c3 = 0, h3 = "";
    for (let u3 = 0; u3 < e5.length; u3++) {
      const d3 = e5[u3];
      if (u3 > 0 && (c3 === n3 && (h3 += o2.decode(a3), c3 = 0), a3[c3++] = s2), 0 !== d3.length) {
        i3[0] = 0;
        for (let e6 = 0; e6 < d3.length; e6++) {
          const s3 = d3[e6];
          c3 > r3 && (h3 += o2.decode(l3), a3.copyWithin(0, r3, c3), c3 -= r3), e6 > 0 && (a3[c3++] = t2), c3 = f2(a3, c3, i3, s3, 0), 1 !== s3.length && (c3 = f2(a3, c3, i3, s3, 1), c3 = f2(a3, c3, i3, s3, 2), c3 = f2(a3, c3, i3, s3, 3), 4 !== s3.length && (c3 = f2(a3, c3, i3, s3, 4)));
        }
      }
    }
    return h3 + o2.decode(a3.subarray(0, c3));
  }
  function f2(e5, t3, s3, i3, r3) {
    const o3 = i3[r3];
    let a3 = o3 - s3[r3];
    s3[r3] = o3, a3 = a3 < 0 ? -a3 << 1 | 1 : a3 << 1;
    do {
      let s4 = 31 & a3;
      a3 >>>= 5, a3 > 0 && (s4 |= 32), e5[t3++] = n2[s4];
    } while (a3 > 0);
    return t3;
  }
  e4.decode = a2, e4.encode = p2, Object.defineProperty(e4, "__esModule", { value: true });
}(s.exports);
var i = s.exports;
class n {
  constructor(e4) {
    this.bits = e4 instanceof n ? e4.bits.slice() : [];
  }
  add(e4) {
    this.bits[e4 >> 5] |= 1 << (31 & e4);
  }
  has(e4) {
    return !!(this.bits[e4 >> 5] & 1 << (31 & e4));
  }
}
let r = class e2 {
  constructor(e4, t2, s2) {
    this.start = e4, this.end = t2, this.original = s2, this.intro = "", this.outro = "", this.content = s2, this.storeName = false, this.edited = false, this.previous = null, this.next = null;
  }
  appendLeft(e4) {
    this.outro += e4;
  }
  appendRight(e4) {
    this.intro = this.intro + e4;
  }
  clone() {
    const t2 = new e2(this.start, this.end, this.original);
    return t2.intro = this.intro, t2.outro = this.outro, t2.content = this.content, t2.storeName = this.storeName, t2.edited = this.edited, t2;
  }
  contains(e4) {
    return this.start < e4 && e4 < this.end;
  }
  eachNext(e4) {
    let t2 = this;
    for (; t2; )
      e4(t2), t2 = t2.next;
  }
  eachPrevious(e4) {
    let t2 = this;
    for (; t2; )
      e4(t2), t2 = t2.previous;
  }
  edit(e4, t2, s2) {
    return this.content = e4, s2 || (this.intro = "", this.outro = ""), this.storeName = t2, this.edited = true, this;
  }
  prependLeft(e4) {
    this.outro = e4 + this.outro;
  }
  prependRight(e4) {
    this.intro = e4 + this.intro;
  }
  split(t2) {
    const s2 = t2 - this.start, i2 = this.original.slice(0, s2), n2 = this.original.slice(s2);
    this.original = i2;
    const r2 = new e2(t2, this.end, n2);
    return r2.outro = this.outro, this.outro = "", this.end = t2, this.edited ? (r2.edit("", false), this.content = "") : this.content = i2, r2.next = this.next, r2.next && (r2.next.previous = r2), r2.previous = this, this.next = r2, r2;
  }
  toString() {
    return this.intro + this.content + this.outro;
  }
  trimEnd(e4) {
    if (this.outro = this.outro.replace(e4, ""), this.outro.length)
      return true;
    const t2 = this.content.replace(e4, "");
    return t2.length ? (t2 !== this.content && (this.split(this.start + t2.length).edit("", void 0, true), this.edited && this.edit(t2, this.storeName, true)), true) : (this.edit("", void 0, true), this.intro = this.intro.replace(e4, ""), !!this.intro.length || void 0);
  }
  trimStart(e4) {
    if (this.intro = this.intro.replace(e4, ""), this.intro.length)
      return true;
    const t2 = this.content.replace(e4, "");
    if (t2.length) {
      if (t2 !== this.content) {
        const e5 = this.split(this.end - t2.length);
        this.edited && e5.edit(t2, this.storeName, true), this.edit("", void 0, true);
      }
      return true;
    }
    return this.edit("", void 0, true), this.outro = this.outro.replace(e4, ""), !!this.outro.length || void 0;
  }
};
function o() {
  return "undefined" != typeof window && "function" == typeof window.btoa ? (e4) => window.btoa(unescape(encodeURIComponent(e4))) : "function" == typeof Buffer ? (e4) => Buffer.from(e4, "utf-8").toString("base64") : () => {
    throw new Error("Unsupported environment: `window.btoa` or `Buffer` should be supported.");
  };
}
const a = o();
class l {
  constructor(e4) {
    this.version = 3, this.file = e4.file, this.sources = e4.sources, this.sourcesContent = e4.sourcesContent, this.names = e4.names, this.mappings = i.encode(e4.mappings), void 0 !== e4.x_google_ignoreList && (this.x_google_ignoreList = e4.x_google_ignoreList);
  }
  toString() {
    return JSON.stringify(this);
  }
  toUrl() {
    return "data:application/json;charset=utf-8;base64," + a(this.toString());
  }
}
function c(e4, t2) {
  const s2 = e4.split(/[/\\]/), i2 = t2.split(/[/\\]/);
  for (s2.pop(); s2[0] === i2[0]; )
    s2.shift(), i2.shift();
  if (s2.length) {
    let e5 = s2.length;
    for (; e5--; )
      s2[e5] = "..";
  }
  return s2.concat(i2).join("/");
}
const h = Object.prototype.toString;
function u(e4) {
  return "[object Object]" === h.call(e4);
}
function d(e4) {
  const t2 = e4.split("\n"), s2 = [];
  for (let e5 = 0, i2 = 0; e5 < t2.length; e5++)
    s2.push(i2), i2 += t2[e5].length + 1;
  return function(e5) {
    let t3 = 0, i2 = s2.length;
    for (; t3 < i2; ) {
      const n3 = t3 + i2 >> 1;
      e5 < s2[n3] ? i2 = n3 : t3 = n3 + 1;
    }
    const n2 = t3 - 1;
    return { line: n2, column: e5 - s2[n2] };
  };
}
const p = /\w/;
class f {
  constructor(e4) {
    this.hires = e4, this.generatedCodeLine = 0, this.generatedCodeColumn = 0, this.raw = [], this.rawSegments = this.raw[this.generatedCodeLine] = [], this.pending = null;
  }
  addEdit(e4, t2, s2, i2) {
    if (t2.length) {
      const t3 = [this.generatedCodeColumn, e4, s2.line, s2.column];
      i2 >= 0 && t3.push(i2), this.rawSegments.push(t3);
    } else
      this.pending && this.rawSegments.push(this.pending);
    this.advance(t2), this.pending = null;
  }
  addUneditedChunk(e4, t2, s2, i2, n2) {
    let r2 = t2.start, o2 = true, a2 = false;
    for (; r2 < t2.end; ) {
      if (this.hires || o2 || n2.has(r2)) {
        const t3 = [this.generatedCodeColumn, e4, i2.line, i2.column];
        "boundary" === this.hires ? p.test(s2[r2]) ? a2 || (this.rawSegments.push(t3), a2 = true) : (this.rawSegments.push(t3), a2 = false) : this.rawSegments.push(t3);
      }
      "\n" === s2[r2] ? (i2.line += 1, i2.column = 0, this.generatedCodeLine += 1, this.raw[this.generatedCodeLine] = this.rawSegments = [], this.generatedCodeColumn = 0, o2 = true) : (i2.column += 1, this.generatedCodeColumn += 1, o2 = false), r2 += 1;
    }
    this.pending = null;
  }
  advance(e4) {
    if (!e4)
      return;
    const t2 = e4.split("\n");
    if (t2.length > 1) {
      for (let e5 = 0; e5 < t2.length - 1; e5++)
        this.generatedCodeLine++, this.raw[this.generatedCodeLine] = this.rawSegments = [];
      this.generatedCodeColumn = 0;
    }
    this.generatedCodeColumn += t2[t2.length - 1].length;
  }
}
const m = "\n", g = { insertLeft: false, insertRight: false, storeName: false };
class y {
  constructor(e4, t2 = {}) {
    const s2 = new r(0, e4.length, e4);
    Object.defineProperties(this, { original: { writable: true, value: e4 }, outro: { writable: true, value: "" }, intro: { writable: true, value: "" }, firstChunk: { writable: true, value: s2 }, lastChunk: { writable: true, value: s2 }, lastSearchedChunk: { writable: true, value: s2 }, byStart: { writable: true, value: {} }, byEnd: { writable: true, value: {} }, filename: { writable: true, value: t2.filename }, indentExclusionRanges: { writable: true, value: t2.indentExclusionRanges }, sourcemapLocations: { writable: true, value: new n() }, storedNames: { writable: true, value: {} }, indentStr: { writable: true, value: void 0 }, ignoreList: { writable: true, value: t2.ignoreList } }), this.byStart[0] = s2, this.byEnd[e4.length] = s2;
  }
  addSourcemapLocation(e4) {
    this.sourcemapLocations.add(e4);
  }
  append(e4) {
    if ("string" != typeof e4)
      throw new TypeError("outro content must be a string");
    return this.outro += e4, this;
  }
  appendLeft(e4, t2) {
    if ("string" != typeof t2)
      throw new TypeError("inserted content must be a string");
    this._split(e4);
    const s2 = this.byEnd[e4];
    return s2 ? s2.appendLeft(t2) : this.intro += t2, this;
  }
  appendRight(e4, t2) {
    if ("string" != typeof t2)
      throw new TypeError("inserted content must be a string");
    this._split(e4);
    const s2 = this.byStart[e4];
    return s2 ? s2.appendRight(t2) : this.outro += t2, this;
  }
  clone() {
    const e4 = new y(this.original, { filename: this.filename });
    let t2 = this.firstChunk, s2 = e4.firstChunk = e4.lastSearchedChunk = t2.clone();
    for (; t2; ) {
      e4.byStart[s2.start] = s2, e4.byEnd[s2.end] = s2;
      const i2 = t2.next, n2 = i2 && i2.clone();
      n2 && (s2.next = n2, n2.previous = s2, s2 = n2), t2 = i2;
    }
    return e4.lastChunk = s2, this.indentExclusionRanges && (e4.indentExclusionRanges = this.indentExclusionRanges.slice()), e4.sourcemapLocations = new n(this.sourcemapLocations), e4.intro = this.intro, e4.outro = this.outro, e4;
  }
  generateDecodedMap(e4) {
    e4 = e4 || {};
    const t2 = Object.keys(this.storedNames), s2 = new f(e4.hires), i2 = d(this.original);
    return this.intro && s2.advance(this.intro), this.firstChunk.eachNext((e5) => {
      const n2 = i2(e5.start);
      e5.intro.length && s2.advance(e5.intro), e5.edited ? s2.addEdit(0, e5.content, n2, e5.storeName ? t2.indexOf(e5.original) : -1) : s2.addUneditedChunk(0, e5, this.original, n2, this.sourcemapLocations), e5.outro.length && s2.advance(e5.outro);
    }), { file: e4.file ? e4.file.split(/[/\\]/).pop() : void 0, sources: [e4.source ? c(e4.file || "", e4.source) : e4.file || ""], sourcesContent: e4.includeContent ? [this.original] : void 0, names: t2, mappings: s2.raw, x_google_ignoreList: this.ignoreList ? [0] : void 0 };
  }
  generateMap(e4) {
    return new l(this.generateDecodedMap(e4));
  }
  _ensureindentStr() {
    void 0 === this.indentStr && (this.indentStr = function(e4) {
      const t2 = e4.split("\n"), s2 = t2.filter((e5) => /^\t+/.test(e5)), i2 = t2.filter((e5) => /^ {2,}/.test(e5));
      if (0 === s2.length && 0 === i2.length)
        return null;
      if (s2.length >= i2.length)
        return "	";
      const n2 = i2.reduce((e5, t3) => {
        const s3 = /^ +/.exec(t3)[0].length;
        return Math.min(s3, e5);
      }, 1 / 0);
      return new Array(n2 + 1).join(" ");
    }(this.original));
  }
  _getRawIndentString() {
    return this._ensureindentStr(), this.indentStr;
  }
  getIndentString() {
    return this._ensureindentStr(), null === this.indentStr ? "	" : this.indentStr;
  }
  indent(e4, t2) {
    const s2 = /^[^\r\n]/gm;
    if (u(e4) && (t2 = e4, e4 = void 0), void 0 === e4 && (this._ensureindentStr(), e4 = this.indentStr || "	"), "" === e4)
      return this;
    const i2 = {};
    if ((t2 = t2 || {}).exclude) {
      ("number" == typeof t2.exclude[0] ? [t2.exclude] : t2.exclude).forEach((e5) => {
        for (let t3 = e5[0]; t3 < e5[1]; t3 += 1)
          i2[t3] = true;
      });
    }
    let n2 = false !== t2.indentStart;
    const r2 = (t3) => n2 ? `${e4}${t3}` : (n2 = true, t3);
    this.intro = this.intro.replace(s2, r2);
    let o2 = 0, a2 = this.firstChunk;
    for (; a2; ) {
      const t3 = a2.end;
      if (a2.edited)
        i2[o2] || (a2.content = a2.content.replace(s2, r2), a2.content.length && (n2 = "\n" === a2.content[a2.content.length - 1]));
      else
        for (o2 = a2.start; o2 < t3; ) {
          if (!i2[o2]) {
            const t4 = this.original[o2];
            "\n" === t4 ? n2 = true : "\r" !== t4 && n2 && (n2 = false, o2 === a2.start || (this._splitChunk(a2, o2), a2 = a2.next), a2.prependRight(e4));
          }
          o2 += 1;
        }
      o2 = a2.end, a2 = a2.next;
    }
    return this.outro = this.outro.replace(s2, r2), this;
  }
  insert() {
    throw new Error("magicString.insert(...) is deprecated. Use prependRight(...) or appendLeft(...)");
  }
  insertLeft(e4, t2) {
    return g.insertLeft || (console.warn("magicString.insertLeft(...) is deprecated. Use magicString.appendLeft(...) instead"), g.insertLeft = true), this.appendLeft(e4, t2);
  }
  insertRight(e4, t2) {
    return g.insertRight || (console.warn("magicString.insertRight(...) is deprecated. Use magicString.prependRight(...) instead"), g.insertRight = true), this.prependRight(e4, t2);
  }
  move(e4, t2, s2) {
    if (s2 >= e4 && s2 <= t2)
      throw new Error("Cannot move a selection inside itself");
    this._split(e4), this._split(t2), this._split(s2);
    const i2 = this.byStart[e4], n2 = this.byEnd[t2], r2 = i2.previous, o2 = n2.next, a2 = this.byStart[s2];
    if (!a2 && n2 === this.lastChunk)
      return this;
    const l2 = a2 ? a2.previous : this.lastChunk;
    return r2 && (r2.next = o2), o2 && (o2.previous = r2), l2 && (l2.next = i2), a2 && (a2.previous = n2), i2.previous || (this.firstChunk = n2.next), n2.next || (this.lastChunk = i2.previous, this.lastChunk.next = null), i2.previous = l2, n2.next = a2 || null, l2 || (this.firstChunk = i2), a2 || (this.lastChunk = n2), this;
  }
  overwrite(e4, t2, s2, i2) {
    return i2 = i2 || {}, this.update(e4, t2, s2, { ...i2, overwrite: !i2.contentOnly });
  }
  update(e4, t2, s2, i2) {
    if ("string" != typeof s2)
      throw new TypeError("replacement content must be a string");
    for (; e4 < 0; )
      e4 += this.original.length;
    for (; t2 < 0; )
      t2 += this.original.length;
    if (t2 > this.original.length)
      throw new Error("end is out of bounds");
    if (e4 === t2)
      throw new Error("Cannot overwrite a zero-length range – use appendLeft or prependRight instead");
    this._split(e4), this._split(t2), true === i2 && (g.storeName || (console.warn("The final argument to magicString.overwrite(...) should be an options object. See https://github.com/rich-harris/magic-string"), g.storeName = true), i2 = { storeName: true });
    const n2 = void 0 !== i2 && i2.storeName, o2 = void 0 !== i2 && i2.overwrite;
    if (n2) {
      const s3 = this.original.slice(e4, t2);
      Object.defineProperty(this.storedNames, s3, { writable: true, value: true, enumerable: true });
    }
    const a2 = this.byStart[e4], l2 = this.byEnd[t2];
    if (a2) {
      let e5 = a2;
      for (; e5 !== l2; ) {
        if (e5.next !== this.byStart[e5.end])
          throw new Error("Cannot overwrite across a split point");
        e5 = e5.next, e5.edit("", false);
      }
      a2.edit(s2, n2, !o2);
    } else {
      const i3 = new r(e4, t2, "").edit(s2, n2);
      l2.next = i3, i3.previous = l2;
    }
    return this;
  }
  prepend(e4) {
    if ("string" != typeof e4)
      throw new TypeError("outro content must be a string");
    return this.intro = e4 + this.intro, this;
  }
  prependLeft(e4, t2) {
    if ("string" != typeof t2)
      throw new TypeError("inserted content must be a string");
    this._split(e4);
    const s2 = this.byEnd[e4];
    return s2 ? s2.prependLeft(t2) : this.intro = t2 + this.intro, this;
  }
  prependRight(e4, t2) {
    if ("string" != typeof t2)
      throw new TypeError("inserted content must be a string");
    this._split(e4);
    const s2 = this.byStart[e4];
    return s2 ? s2.prependRight(t2) : this.outro = t2 + this.outro, this;
  }
  remove(e4, t2) {
    for (; e4 < 0; )
      e4 += this.original.length;
    for (; t2 < 0; )
      t2 += this.original.length;
    if (e4 === t2)
      return this;
    if (e4 < 0 || t2 > this.original.length)
      throw new Error("Character is out of bounds");
    if (e4 > t2)
      throw new Error("end must be greater than start");
    this._split(e4), this._split(t2);
    let s2 = this.byStart[e4];
    for (; s2; )
      s2.intro = "", s2.outro = "", s2.edit(""), s2 = t2 > s2.end ? this.byStart[s2.end] : null;
    return this;
  }
  lastChar() {
    if (this.outro.length)
      return this.outro[this.outro.length - 1];
    let e4 = this.lastChunk;
    do {
      if (e4.outro.length)
        return e4.outro[e4.outro.length - 1];
      if (e4.content.length)
        return e4.content[e4.content.length - 1];
      if (e4.intro.length)
        return e4.intro[e4.intro.length - 1];
    } while (e4 = e4.previous);
    return this.intro.length ? this.intro[this.intro.length - 1] : "";
  }
  lastLine() {
    let e4 = this.outro.lastIndexOf(m);
    if (-1 !== e4)
      return this.outro.substr(e4 + 1);
    let t2 = this.outro, s2 = this.lastChunk;
    do {
      if (s2.outro.length > 0) {
        if (e4 = s2.outro.lastIndexOf(m), -1 !== e4)
          return s2.outro.substr(e4 + 1) + t2;
        t2 = s2.outro + t2;
      }
      if (s2.content.length > 0) {
        if (e4 = s2.content.lastIndexOf(m), -1 !== e4)
          return s2.content.substr(e4 + 1) + t2;
        t2 = s2.content + t2;
      }
      if (s2.intro.length > 0) {
        if (e4 = s2.intro.lastIndexOf(m), -1 !== e4)
          return s2.intro.substr(e4 + 1) + t2;
        t2 = s2.intro + t2;
      }
    } while (s2 = s2.previous);
    return e4 = this.intro.lastIndexOf(m), -1 !== e4 ? this.intro.substr(e4 + 1) + t2 : this.intro + t2;
  }
  slice(e4 = 0, t2 = this.original.length) {
    for (; e4 < 0; )
      e4 += this.original.length;
    for (; t2 < 0; )
      t2 += this.original.length;
    let s2 = "", i2 = this.firstChunk;
    for (; i2 && (i2.start > e4 || i2.end <= e4); ) {
      if (i2.start < t2 && i2.end >= t2)
        return s2;
      i2 = i2.next;
    }
    if (i2 && i2.edited && i2.start !== e4)
      throw new Error(`Cannot use replaced character ${e4} as slice start anchor.`);
    const n2 = i2;
    for (; i2; ) {
      !i2.intro || n2 === i2 && i2.start !== e4 || (s2 += i2.intro);
      const r2 = i2.start < t2 && i2.end >= t2;
      if (r2 && i2.edited && i2.end !== t2)
        throw new Error(`Cannot use replaced character ${t2} as slice end anchor.`);
      const o2 = n2 === i2 ? e4 - i2.start : 0, a2 = r2 ? i2.content.length + t2 - i2.end : i2.content.length;
      if (s2 += i2.content.slice(o2, a2), !i2.outro || r2 && i2.end !== t2 || (s2 += i2.outro), r2)
        break;
      i2 = i2.next;
    }
    return s2;
  }
  snip(e4, t2) {
    const s2 = this.clone();
    return s2.remove(0, e4), s2.remove(t2, s2.original.length), s2;
  }
  _split(e4) {
    if (this.byStart[e4] || this.byEnd[e4])
      return;
    let t2 = this.lastSearchedChunk;
    const s2 = e4 > t2.end;
    for (; t2; ) {
      if (t2.contains(e4))
        return this._splitChunk(t2, e4);
      t2 = s2 ? this.byStart[t2.end] : this.byEnd[t2.start];
    }
  }
  _splitChunk(e4, t2) {
    if (e4.edited && e4.content.length) {
      const s3 = d(this.original)(t2);
      throw new Error(`Cannot split a chunk that has already been edited (${s3.line}:${s3.column} – "${e4.original}")`);
    }
    const s2 = e4.split(t2);
    return this.byEnd[t2] = e4, this.byStart[t2] = s2, this.byEnd[s2.end] = s2, e4 === this.lastChunk && (this.lastChunk = s2), this.lastSearchedChunk = e4, true;
  }
  toString() {
    let e4 = this.intro, t2 = this.firstChunk;
    for (; t2; )
      e4 += t2.toString(), t2 = t2.next;
    return e4 + this.outro;
  }
  isEmpty() {
    let e4 = this.firstChunk;
    do {
      if (e4.intro.length && e4.intro.trim() || e4.content.length && e4.content.trim() || e4.outro.length && e4.outro.trim())
        return false;
    } while (e4 = e4.next);
    return true;
  }
  length() {
    let e4 = this.firstChunk, t2 = 0;
    do {
      t2 += e4.intro.length + e4.content.length + e4.outro.length;
    } while (e4 = e4.next);
    return t2;
  }
  trimLines() {
    return this.trim("[\\r\\n]");
  }
  trim(e4) {
    return this.trimStart(e4).trimEnd(e4);
  }
  trimEndAborted(e4) {
    const t2 = new RegExp((e4 || "\\s") + "+$");
    if (this.outro = this.outro.replace(t2, ""), this.outro.length)
      return true;
    let s2 = this.lastChunk;
    do {
      const e5 = s2.end, i2 = s2.trimEnd(t2);
      if (s2.end !== e5 && (this.lastChunk === s2 && (this.lastChunk = s2.next), this.byEnd[s2.end] = s2, this.byStart[s2.next.start] = s2.next, this.byEnd[s2.next.end] = s2.next), i2)
        return true;
      s2 = s2.previous;
    } while (s2);
    return false;
  }
  trimEnd(e4) {
    return this.trimEndAborted(e4), this;
  }
  trimStartAborted(e4) {
    const t2 = new RegExp("^" + (e4 || "\\s") + "+");
    if (this.intro = this.intro.replace(t2, ""), this.intro.length)
      return true;
    let s2 = this.firstChunk;
    do {
      const e5 = s2.end, i2 = s2.trimStart(t2);
      if (s2.end !== e5 && (s2 === this.lastChunk && (this.lastChunk = s2.next), this.byEnd[s2.end] = s2, this.byStart[s2.next.start] = s2.next, this.byEnd[s2.next.end] = s2.next), i2)
        return true;
      s2 = s2.next;
    } while (s2);
    return false;
  }
  trimStart(e4) {
    return this.trimStartAborted(e4), this;
  }
  hasChanged() {
    return this.original !== this.toString();
  }
  _replaceRegexp(e4, t2) {
    function s2(e5, s3) {
      return "string" == typeof t2 ? t2.replace(/\$(\$|&|\d+)/g, (t3, s4) => {
        if ("$" === s4)
          return "$";
        if ("&" === s4)
          return e5[0];
        return +s4 < e5.length ? e5[+s4] : `$${s4}`;
      }) : t2(...e5, e5.index, s3, e5.groups);
    }
    if (e4.global) {
      (function(e5, t3) {
        let s3;
        const i2 = [];
        for (; s3 = e5.exec(t3); )
          i2.push(s3);
        return i2;
      })(e4, this.original).forEach((e5) => {
        null != e5.index && this.overwrite(e5.index, e5.index + e5[0].length, s2(e5, this.original));
      });
    } else {
      const t3 = this.original.match(e4);
      t3 && null != t3.index && this.overwrite(t3.index, t3.index + t3[0].length, s2(t3, this.original));
    }
    return this;
  }
  _replaceString(e4, t2) {
    const { original: s2 } = this, i2 = s2.indexOf(e4);
    return -1 !== i2 && this.overwrite(i2, i2 + e4.length, t2), this;
  }
  replace(e4, t2) {
    return "string" == typeof e4 ? this._replaceString(e4, t2) : this._replaceRegexp(e4, t2);
  }
  _replaceAllString(e4, t2) {
    const { original: s2 } = this, i2 = e4.length;
    for (let n2 = s2.indexOf(e4); -1 !== n2; n2 = s2.indexOf(e4, n2 + i2))
      this.overwrite(n2, n2 + i2, t2);
    return this;
  }
  replaceAll(e4, t2) {
    if ("string" == typeof e4)
      return this._replaceAllString(e4, t2);
    if (!e4.global)
      throw new TypeError("MagicString.prototype.replaceAll called with a non-global RegExp argument");
    return this._replaceRegexp(e4, t2);
  }
}
const x = Object.prototype.hasOwnProperty;
let E = class e3 {
  constructor(e4 = {}) {
    this.intro = e4.intro || "", this.separator = void 0 !== e4.separator ? e4.separator : "\n", this.sources = [], this.uniqueSources = [], this.uniqueSourceIndexByFilename = {};
  }
  addSource(e4) {
    if (e4 instanceof y)
      return this.addSource({ content: e4, filename: e4.filename, separator: this.separator });
    if (!u(e4) || !e4.content)
      throw new Error("bundle.addSource() takes an object with a `content` property, which should be an instance of MagicString, and an optional `filename`");
    if (["filename", "ignoreList", "indentExclusionRanges", "separator"].forEach((t2) => {
      x.call(e4, t2) || (e4[t2] = e4.content[t2]);
    }), void 0 === e4.separator && (e4.separator = this.separator), e4.filename)
      if (x.call(this.uniqueSourceIndexByFilename, e4.filename)) {
        const t2 = this.uniqueSources[this.uniqueSourceIndexByFilename[e4.filename]];
        if (e4.content.original !== t2.content)
          throw new Error(`Illegal source: same filename (${e4.filename}), different contents`);
      } else
        this.uniqueSourceIndexByFilename[e4.filename] = this.uniqueSources.length, this.uniqueSources.push({ filename: e4.filename, content: e4.content.original });
    return this.sources.push(e4), this;
  }
  append(e4, t2) {
    return this.addSource({ content: new y(e4), separator: t2 && t2.separator || "" }), this;
  }
  clone() {
    const t2 = new e3({ intro: this.intro, separator: this.separator });
    return this.sources.forEach((e4) => {
      t2.addSource({ filename: e4.filename, content: e4.content.clone(), separator: e4.separator });
    }), t2;
  }
  generateDecodedMap(e4 = {}) {
    const t2 = [];
    let s2;
    this.sources.forEach((e5) => {
      Object.keys(e5.content.storedNames).forEach((e6) => {
        ~t2.indexOf(e6) || t2.push(e6);
      });
    });
    const i2 = new f(e4.hires);
    return this.intro && i2.advance(this.intro), this.sources.forEach((e5, n2) => {
      n2 > 0 && i2.advance(this.separator);
      const r2 = e5.filename ? this.uniqueSourceIndexByFilename[e5.filename] : -1, o2 = e5.content, a2 = d(o2.original);
      o2.intro && i2.advance(o2.intro), o2.firstChunk.eachNext((s3) => {
        const n3 = a2(s3.start);
        s3.intro.length && i2.advance(s3.intro), e5.filename ? s3.edited ? i2.addEdit(r2, s3.content, n3, s3.storeName ? t2.indexOf(s3.original) : -1) : i2.addUneditedChunk(r2, s3, o2.original, n3, o2.sourcemapLocations) : i2.advance(s3.content), s3.outro.length && i2.advance(s3.outro);
      }), o2.outro && i2.advance(o2.outro), e5.ignoreList && -1 !== r2 && (void 0 === s2 && (s2 = []), s2.push(r2));
    }), { file: e4.file ? e4.file.split(/[/\\]/).pop() : void 0, sources: this.uniqueSources.map((t3) => e4.file ? c(e4.file, t3.filename) : t3.filename), sourcesContent: this.uniqueSources.map((t3) => e4.includeContent ? t3.content : null), names: t2, mappings: i2.raw, x_google_ignoreList: s2 };
  }
  generateMap(e4) {
    return new l(this.generateDecodedMap(e4));
  }
  getIndentString() {
    const e4 = {};
    return this.sources.forEach((t2) => {
      const s2 = t2.content._getRawIndentString();
      null !== s2 && (e4[s2] || (e4[s2] = 0), e4[s2] += 1);
    }), Object.keys(e4).sort((t2, s2) => e4[t2] - e4[s2])[0] || "	";
  }
  indent(e4) {
    if (arguments.length || (e4 = this.getIndentString()), "" === e4)
      return this;
    let t2 = !this.intro || "\n" === this.intro.slice(-1);
    return this.sources.forEach((s2, i2) => {
      const n2 = void 0 !== s2.separator ? s2.separator : this.separator, r2 = t2 || i2 > 0 && /\r?\n$/.test(n2);
      s2.content.indent(e4, { exclude: s2.indentExclusionRanges, indentStart: r2 }), t2 = "\n" === s2.content.lastChar();
    }), this.intro && (this.intro = e4 + this.intro.replace(/^[^\n]/gm, (t3, s2) => s2 > 0 ? e4 + t3 : t3)), this;
  }
  prepend(e4) {
    return this.intro = e4 + this.intro, this;
  }
  toString() {
    const e4 = this.sources.map((e5, t2) => {
      const s2 = void 0 !== e5.separator ? e5.separator : this.separator;
      return (t2 > 0 ? s2 : "") + e5.content.toString();
    }).join("");
    return this.intro + e4;
  }
  isEmpty() {
    return (!this.intro.length || !this.intro.trim()) && !this.sources.some((e4) => !e4.content.isEmpty());
  }
  length() {
    return this.sources.reduce((e4, t2) => e4 + t2.content.length(), this.intro.length);
  }
  trimLines() {
    return this.trim("[\\r\\n]");
  }
  trim(e4) {
    return this.trimStart(e4).trimEnd(e4);
  }
  trimStart(e4) {
    const t2 = new RegExp("^" + (e4 || "\\s") + "+");
    if (this.intro = this.intro.replace(t2, ""), !this.intro) {
      let t3, s2 = 0;
      do {
        if (t3 = this.sources[s2++], !t3)
          break;
      } while (!t3.content.trimStartAborted(e4));
    }
    return this;
  }
  trimEnd(e4) {
    const t2 = new RegExp((e4 || "\\s") + "+$");
    let s2, i2 = this.sources.length - 1;
    do {
      if (s2 = this.sources[i2--], !s2) {
        this.intro = this.intro.replace(t2, "");
        break;
      }
    } while (!s2.content.trimEndAborted(e4));
    return this;
  }
};
const b = /^(?:\/|(?:[A-Za-z]:)?[/\\|])/, v = /^\.?\.\//, S = /\\/g, A = /[/\\]/, k = /\.[^.]+$/;
function I(e4) {
  return b.test(e4);
}
function w(e4) {
  return v.test(e4);
}
function P(e4) {
  return e4.replace(S, "/");
}
function C(e4) {
  return e4.split(A).pop() || "";
}
function $(e4) {
  const t2 = /[/\\][^/\\]*$/.exec(e4);
  if (!t2)
    return ".";
  return e4.slice(0, -t2[0].length) || "/";
}
function N(e4) {
  const t2 = k.exec(C(e4));
  return t2 ? t2[0] : "";
}
function _(e4, t2) {
  const s2 = e4.split(A).filter(Boolean), i2 = t2.split(A).filter(Boolean);
  for ("." === s2[0] && s2.shift(), "." === i2[0] && i2.shift(); s2[0] && i2[0] && s2[0] === i2[0]; )
    s2.shift(), i2.shift();
  for (; ".." === i2[0] && s2.length > 0; )
    i2.shift(), s2.pop();
  for (; s2.pop(); )
    i2.unshift("..");
  return i2.join("/");
}
function R(...e4) {
  const t2 = e4.shift();
  if (!t2)
    return "/";
  let s2 = t2.split(A);
  for (const t3 of e4)
    if (I(t3))
      s2 = t3.split(A);
    else {
      const e5 = t3.split(A);
      for (; "." === e5[0] || ".." === e5[0]; ) {
        ".." === e5.shift() && s2.pop();
      }
      s2.push(...e5);
    }
  return s2.join("/");
}
const M = /[\n\r'\\\u2028\u2029]/, O = /([\n\r'\u2028\u2029])/g, D = /\\/g;
function L(e4) {
  return M.test(e4) ? e4.replace(D, "\\\\").replace(O, "\\$1") : e4;
}
function T(e4) {
  const t2 = C(e4);
  return t2.slice(0, Math.max(0, t2.length - N(e4).length));
}
function V(e4) {
  return I(e4) ? _(R(), e4) : e4;
}
function B(e4) {
  return "/" === e4[0] || "." === e4[0] && ("/" === e4[1] || "." === e4[1]) || I(e4);
}
const z = /^(\.\.\/)*\.\.$/;
function F(e4, t2, s2, i2) {
  for (; t2.startsWith("../"); )
    t2 = t2.slice(3), e4 = "_/" + e4;
  let n2 = P(_($(e4), t2));
  if (s2 && n2.endsWith(".js") && (n2 = n2.slice(0, -3)), i2) {
    if ("" === n2)
      return "../" + C(t2);
    if (z.test(n2))
      return [...n2.split("/"), "..", C(t2)].join("/");
  }
  return n2 ? n2.startsWith("..") ? n2 : "./" + n2 : ".";
}
class j {
  constructor(e4, t2, s2) {
    this.options = t2, this.inputBase = s2, this.defaultVariableName = "", this.namespaceVariableName = "", this.variableName = "", this.fileName = null, this.importAssertions = null, this.id = e4.id, this.moduleInfo = e4.info, this.renormalizeRenderPath = e4.renormalizeRenderPath, this.suggestedVariableName = e4.suggestedVariableName;
  }
  getFileName() {
    if (this.fileName)
      return this.fileName;
    const { paths: e4 } = this.options;
    return this.fileName = ("function" == typeof e4 ? e4(this.id) : e4[this.id]) || (this.renormalizeRenderPath ? P(_(this.inputBase, this.id)) : this.id);
  }
  getImportAssertions(e4) {
    return this.importAssertions || (this.importAssertions = function(e5, { getObject: t2 }) {
      if (!e5)
        return null;
      const s2 = Object.entries(e5).map(([e6, t3]) => [e6, `'${t3}'`]);
      if (s2.length > 0)
        return t2(s2, { lineBreakIndent: null });
      return null;
    }("es" === this.options.format && this.options.externalImportAssertions && this.moduleInfo.assertions, e4));
  }
  getImportPath(e4) {
    return L(this.renormalizeRenderPath ? F(e4, this.getFileName(), "amd" === this.options.format, false) : this.getFileName());
  }
}
function U(e4, t2, s2) {
  const i2 = e4.get(t2);
  if (void 0 !== i2)
    return i2;
  const n2 = s2();
  return e4.set(t2, n2), n2;
}
function G() {
  return /* @__PURE__ */ new Set();
}
function W() {
  return [];
}
const q = Symbol("Unknown Key"), H = Symbol("Unknown Non-Accessor Key"), K = Symbol("Unknown Integer"), Y = Symbol("Symbol.toStringTag"), X = [], Q = [q], Z = [H], J = [K], ee = Symbol("Entities");
class te {
  constructor() {
    this.entityPaths = Object.create(null, { [ee]: { value: /* @__PURE__ */ new Set() } });
  }
  trackEntityAtPathAndGetIfTracked(e4, t2) {
    const s2 = this.getEntities(e4);
    return !!s2.has(t2) || (s2.add(t2), false);
  }
  withTrackedEntityAtPath(e4, t2, s2, i2) {
    const n2 = this.getEntities(e4);
    if (n2.has(t2))
      return i2;
    n2.add(t2);
    const r2 = s2();
    return n2.delete(t2), r2;
  }
  getEntities(e4) {
    let t2 = this.entityPaths;
    for (const s2 of e4)
      t2 = t2[s2] = t2[s2] || Object.create(null, { [ee]: { value: /* @__PURE__ */ new Set() } });
    return t2[ee];
  }
}
const se = new te();
class ie {
  constructor() {
    this.entityPaths = Object.create(null, { [ee]: { value: /* @__PURE__ */ new Map() } });
  }
  trackEntityAtPathAndGetIfTracked(e4, t2, s2) {
    let i2 = this.entityPaths;
    for (const t3 of e4)
      i2 = i2[t3] = i2[t3] || Object.create(null, { [ee]: { value: /* @__PURE__ */ new Map() } });
    const n2 = U(i2[ee], t2, G);
    return !!n2.has(s2) || (n2.add(s2), false);
  }
}
const ne = Symbol("Unknown Value"), re = Symbol("Unknown Truthy Value");
class oe {
  constructor() {
    this.included = false;
  }
  deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2) {
    ce(e4);
  }
  deoptimizePath(e4) {
  }
  getLiteralValueAtPath(e4, t2, s2) {
    return ne;
  }
  getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2) {
    return le;
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    return true;
  }
  include(e4, t2, s2) {
    this.included = true;
  }
  includeCallArguments(e4, t2) {
    for (const s2 of t2)
      s2.include(e4, false);
  }
  shouldBeIncluded(e4) {
    return true;
  }
}
const ae = new class extends oe {
}(), le = [ae, false], ce = (e4) => {
  for (const t2 of e4.args)
    t2 == null ? void 0 : t2.deoptimizePath(Q);
}, he = { args: [null], type: 0 }, ue = { args: [null, ae], type: 1 }, de = { args: [null], type: 2, withNew: false };
class pe extends oe {
  constructor(e4) {
    super(), this.name = e4, this.alwaysRendered = false, this.forbiddenNames = null, this.initReached = false, this.isId = false, this.isReassigned = false, this.kind = null, this.renderBaseName = null, this.renderName = null;
  }
  addReference(e4) {
  }
  forbidName(e4) {
    (this.forbiddenNames || (this.forbiddenNames = /* @__PURE__ */ new Set())).add(e4);
  }
  getBaseVariableName() {
    return this.renderBaseName || this.renderName || this.name;
  }
  getName(e4, t2) {
    if (t2 == null ? void 0 : t2(this))
      return this.name;
    const s2 = this.renderName || this.name;
    return this.renderBaseName ? `${this.renderBaseName}${e4(s2)}` : s2;
  }
  hasEffectsOnInteractionAtPath(e4, { type: t2 }, s2) {
    return 0 !== t2 || e4.length > 0;
  }
  include() {
    this.included = true;
  }
  markCalledFromTryStatement() {
  }
  setRenderNames(e4, t2) {
    this.renderBaseName = e4, this.renderName = t2;
  }
}
class fe extends pe {
  constructor(e4, t2) {
    super(t2), this.referenced = false, this.module = e4, this.isNamespace = "*" === t2;
  }
  addReference(e4) {
    this.referenced = true, "default" !== this.name && "*" !== this.name || this.module.suggestName(e4.name);
  }
  hasEffectsOnInteractionAtPath(e4, { type: t2 }) {
    return 0 !== t2 || e4.length > (this.isNamespace ? 1 : 0);
  }
  include() {
    this.included || (this.included = true, this.module.used = true);
  }
}
const me = Object.freeze(/* @__PURE__ */ Object.create(null)), ge = Object.freeze({}), ye = Object.freeze([]), xe = Object.freeze(new class extends Set {
  add() {
    throw new Error("Cannot add to empty set");
  }
}());
var Ee = /* @__PURE__ */ new Set(["await", "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete", "do", "else", "enum", "eval", "export", "extends", "false", "finally", "for", "function", "if", "implements", "import", "in", "instanceof", "interface", "let", "NaN", "new", "null", "package", "private", "protected", "public", "return", "static", "super", "switch", "this", "throw", "true", "try", "typeof", "undefined", "var", "void", "while", "with", "yield"]);
const be = /[^\w$]/g, ve = (e4) => ((e5) => /\d/.test(e5[0]))(e4) || Ee.has(e4) || "arguments" === e4;
function Se(e4) {
  return e4 = e4.replace(/-(\w)/g, (e5, t2) => t2.toUpperCase()).replace(be, "_"), ve(e4) && (e4 = `_${e4}`), e4 || "_";
}
const Ae = "warn", ke = "info", Ie = "debug", we = { [Ie]: 0, [ke]: 1, silent: 3, [Ae]: 2 };
function Pe(e4, t2) {
  return e4.start <= t2 && t2 < e4.end;
}
function Ce(e4, t2, s2) {
  return function(e5, t3 = {}) {
    const { offsetLine: s3 = 0, offsetColumn: i2 = 0 } = t3;
    let n2 = 0;
    const r2 = e5.split("\n").map((e6, t4) => {
      const s4 = n2 + e6.length + 1, i3 = { start: n2, end: s4, line: t4 };
      return n2 = s4, i3;
    });
    let o2 = 0;
    return function(t4, n3) {
      if ("string" == typeof t4 && (t4 = e5.indexOf(t4, n3 ?? 0)), -1 === t4)
        return;
      let a2 = r2[o2];
      const l2 = t4 >= a2.end ? 1 : -1;
      for (; a2; ) {
        if (Pe(a2, t4))
          return { line: s3 + a2.line, column: i2 + t4 - a2.start, character: t4 };
        o2 += l2, a2 = r2[o2];
      }
    };
  }(e4, s2)(t2, s2 && s2.startIndex);
}
function $e(e4) {
  return e4.replace(/^\t+/, (e5) => e5.split("	").join("  "));
}
const Ne = 120, _e = 10, Re = "...";
function Me(e4, t2, s2) {
  let i2 = e4.split("\n");
  if (t2 > i2.length)
    return "";
  const n2 = Math.max($e(i2[t2 - 1].slice(0, s2)).length + _e + Re.length, Ne), r2 = Math.max(0, t2 - 3);
  let o2 = Math.min(t2 + 2, i2.length);
  for (i2 = i2.slice(r2, o2); !/\S/.test(i2[i2.length - 1]); )
    i2.pop(), o2 -= 1;
  const a2 = String(o2).length;
  return i2.map((e5, i3) => {
    const o3 = r2 + i3 + 1 === t2;
    let l2 = String(i3 + r2 + 1);
    for (; l2.length < a2; )
      l2 = ` ${l2}`;
    let c2 = $e(e5);
    if (c2.length > n2 && (c2 = `${c2.slice(0, n2 - Re.length)}${Re}`), o3) {
      const t3 = function(e6) {
        let t4 = "";
        for (; e6--; )
          t4 += " ";
        return t4;
      }(a2 + 2 + $e(e5.slice(0, s2)).length) + "^";
      return `${l2}: ${c2}
${t3}`;
    }
    return `${l2}: ${c2}`;
  }).join("\n");
}
function Oe(e4, t2) {
  const s2 = e4.length <= 1, i2 = e4.map((e5) => `"${e5}"`);
  let n2 = s2 ? i2[0] : `${i2.slice(0, -1).join(", ")} and ${i2.slice(-1)[0]}`;
  return t2 && (n2 += ` ${s2 ? t2[0] : t2[1]}`), n2;
}
function De(e4) {
  return `https://rollupjs.org/${e4}`;
}
const Le = "troubleshooting/#error-name-is-not-exported-by-module", Te = "troubleshooting/#warning-sourcemap-is-likely-to-be-incorrect", Ve = "configuration-options/#output-amd-id", Be = "configuration-options/#output-dir", ze = "configuration-options/#output-exports", Fe = "configuration-options/#output-extend", je = "configuration-options/#output-format", Ue = "configuration-options/#output-experimentaldeepdynamicchunkoptimization", Ge = "configuration-options/#output-globals", We = "configuration-options/#output-inlinedynamicimports", qe = "configuration-options/#output-interop", He = "configuration-options/#output-manualchunks", Ke = "configuration-options/#output-name", Ye = "configuration-options/#output-sourcemapfile", Xe = "plugin-development/#this-getmoduleinfo";
function Qe(e4) {
  throw e4 instanceof Error || (e4 = Object.assign(new Error(e4.message), e4), Object.defineProperty(e4, "name", { value: "RollupError" })), e4;
}
function Ze(e4, t2, s2, i2) {
  if ("object" == typeof t2) {
    const { line: s3, column: n2 } = t2;
    e4.loc = { column: n2, file: i2, line: s3 };
  } else {
    e4.pos = t2;
    const { line: n2, column: r2 } = Ce(s2, t2, { offsetLine: 1 });
    e4.loc = { column: r2, file: i2, line: n2 };
  }
  if (void 0 === e4.frame) {
    const { line: t3, column: i3 } = e4.loc;
    e4.frame = Me(s2, t3, i3);
  }
}
const Je = "ADDON_ERROR", et = "ALREADY_CLOSED", tt = "ANONYMOUS_PLUGIN_CACHE", st = "ASSET_NOT_FINALISED", it = "CANNOT_EMIT_FROM_OPTIONS_HOOK", nt = "CHUNK_NOT_GENERATED", rt = "CIRCULAR_REEXPORT", ot = "DEPRECATED_FEATURE", at = "DUPLICATE_PLUGIN_NAME", lt = "FILE_NAME_CONFLICT", ct = "ILLEGAL_IDENTIFIER_AS_NAME", ht = "INVALID_CHUNK", ut = "INVALID_EXPORT_OPTION", dt = "INVALID_LOG_POSITION", pt = "INVALID_OPTION", ft = "INVALID_PLUGIN_HOOK", mt = "INVALID_ROLLUP_PHASE", gt = "INVALID_SETASSETSOURCE", yt = "MISSING_EXPORT", xt = "MISSING_GLOBAL_NAME", Et = "MISSING_IMPLICIT_DEPENDANT", bt = "MISSING_NAME_OPTION_FOR_IIFE_EXPORT", vt = "MISSING_NODE_BUILTINS", St = "MISSING_OPTION", At = "MIXED_EXPORTS", kt = "NO_TRANSFORM_MAP_OR_AST_WITHOUT_CODE", It = "OPTIMIZE_CHUNK_STATUS", wt = "PLUGIN_ERROR", Pt = "SOURCEMAP_BROKEN", Ct = "UNEXPECTED_NAMED_IMPORT", $t = "UNKNOWN_OPTION", Nt = "UNRESOLVED_ENTRY", _t = "UNRESOLVED_IMPORT", Rt = "VALIDATION_ERROR";
function Mt() {
  return { code: et, message: 'Bundle is already closed, no more calls to "generate" or "write" are allowed.' };
}
function Ot(e4) {
  return { code: "CANNOT_CALL_NAMESPACE", message: `Cannot call a namespace ("${e4}").` };
}
function Dt({ fileName: e4, code: t2 }, s2) {
  const i2 = { code: "CHUNK_INVALID", message: `Chunk "${e4}" is not valid JavaScript: ${s2.message}.` };
  return Ze(i2, s2.loc, t2, e4), i2;
}
function Lt(e4) {
  return { code: "CIRCULAR_DEPENDENCY", ids: e4, message: `Circular dependency: ${e4.map(V).join(" -> ")}` };
}
function Tt(e4, t2, { line: s2, column: i2 }) {
  return { code: "FIRST_SIDE_EFFECT", message: `First side effect in ${V(t2)} is at (${s2}:${i2})
${Me(e4, s2, i2)}` };
}
function Vt(e4, t2) {
  return { code: "ILLEGAL_REASSIGNMENT", message: `Illegal reassignment of import "${e4}" in "${V(t2)}".` };
}
function Bt(e4, t2, s2, i2) {
  return { code: "INCONSISTENT_IMPORT_ASSERTIONS", message: `Module "${V(i2)}" tried to import "${V(s2)}" with ${zt(t2)} assertions, but it was already imported elsewhere with ${zt(e4)} assertions. Please ensure that import assertions for the same module are always consistent.` };
}
const zt = (e4) => {
  const t2 = Object.entries(e4);
  return 0 === t2.length ? "no" : t2.map(([e5, t3]) => `"${e5}": "${t3}"`).join(", ");
};
function Ft(e4, t2, s2) {
  return { code: ut, message: `"${e4}" was specified for "output.exports", but entry module "${V(s2)}" has the following exports: ${Oe(t2)}`, url: De(ze) };
}
function jt(e4, t2, s2, i2) {
  return { code: pt, message: `Invalid value ${void 0 === i2 ? "" : `${JSON.stringify(i2)} `}for option "${e4}" - ${s2}.`, url: De(t2) };
}
function Ut(e4, t2, s2) {
  const i2 = ".json" === N(s2);
  return { binding: e4, code: yt, exporter: s2, id: t2, message: `"${e4}" is not exported by "${V(s2)}", imported by "${V(t2)}".${i2 ? " (Note that you need @rollup/plugin-json to import JSON files)" : ""}`, url: De(Le) };
}
function Gt(e4) {
  const t2 = [...e4.implicitlyLoadedBefore].map((e5) => V(e5.id)).sort();
  return { code: Et, message: `Module "${V(e4.id)}" that should be implicitly loaded before ${Oe(t2)} is not included in the module graph. Either it was not imported by an included module or only via a tree-shaken dynamic import, or no imported bindings were used and it had otherwise no side-effects.` };
}
function Wt(e4, t2, s2) {
  return { code: It, message: `${s2}, there are
${e4} chunks, of which
${t2} are below minChunkSize.` };
}
function qt(e4, t2, { hook: s2, id: i2 } = {}) {
  const n2 = e4.code;
  return e4.pluginCode || null == n2 || "string" == typeof n2 && ("string" != typeof n2 || n2.startsWith("PLUGIN_")) || (e4.pluginCode = n2), e4.code = wt, e4.plugin = t2, s2 && (e4.hook = s2), i2 && (e4.id = i2), e4;
}
function Ht(e4) {
  return { code: Pt, message: `Multiple conflicting contents for sourcemap source ${e4}` };
}
function Kt(e4, t2, s2) {
  const i2 = s2 ? "reexport" : "import";
  return { code: Ct, exporter: e4, message: `The named export "${t2}" was ${i2}ed from the external module "${V(e4)}" even though its interop type is "defaultOnly". Either remove or change this ${i2} or change the value of the "output.interop" option.`, url: De(qe) };
}
function Yt(e4) {
  return { code: Ct, exporter: e4, message: `There was a namespace "*" reexport from the external module "${V(e4)}" even though its interop type is "defaultOnly". This will be ignored as namespace reexports only reexport named exports. If this is not intended, either remove or change this reexport or change the value of the "output.interop" option.`, url: De(qe) };
}
function Xt(e4) {
  return { code: Rt, message: e4 };
}
function Qt(e4, t2, s2, i2, n2) {
  Zt(e4, t2, s2, i2.onLog, i2.strictDeprecations, n2);
}
function Zt(e4, t2, s2, i2, n2, r2) {
  if (s2 || n2) {
    const s3 = function(e5, t3, s4) {
      return { code: ot, message: e5, url: De(t3), ...s4 ? { plugin: s4 } : {} };
    }(e4, t2, r2);
    if (n2)
      return Qe(s3);
    i2(Ae, s3);
  }
}
class Jt {
  constructor(e4, t2, s2, i2, n2, r2) {
    this.options = e4, this.id = t2, this.renormalizeRenderPath = n2, this.dynamicImporters = [], this.execIndex = 1 / 0, this.exportedVariables = /* @__PURE__ */ new Map(), this.importers = [], this.reexported = false, this.used = false, this.declarations = /* @__PURE__ */ new Map(), this.mostCommonSuggestion = 0, this.nameSuggestions = /* @__PURE__ */ new Map(), this.suggestedVariableName = Se(t2.split(/[/\\]/).pop());
    const { importers: o2, dynamicImporters: a2 } = this, l2 = this.info = { assertions: r2, ast: null, code: null, dynamicallyImportedIdResolutions: ye, dynamicallyImportedIds: ye, get dynamicImporters() {
      return a2.sort();
    }, exportedBindings: null, exports: null, hasDefaultExport: null, get hasModuleSideEffects() {
      return Qt("Accessing ModuleInfo.hasModuleSideEffects from plugins is deprecated. Please use ModuleInfo.moduleSideEffects instead.", Xe, true, e4), l2.moduleSideEffects;
    }, id: t2, implicitlyLoadedAfterOneOf: ye, implicitlyLoadedBefore: ye, importedIdResolutions: ye, importedIds: ye, get importers() {
      return o2.sort();
    }, isEntry: false, isExternal: true, isIncluded: null, meta: i2, moduleSideEffects: s2, syntheticNamedExports: false };
    Object.defineProperty(this.info, "hasModuleSideEffects", { enumerable: false });
  }
  getVariableForExportName(e4) {
    const t2 = this.declarations.get(e4);
    if (t2)
      return [t2];
    const s2 = new fe(this, e4);
    return this.declarations.set(e4, s2), this.exportedVariables.set(s2, e4), [s2];
  }
  suggestName(e4) {
    const t2 = (this.nameSuggestions.get(e4) ?? 0) + 1;
    this.nameSuggestions.set(e4, t2), t2 > this.mostCommonSuggestion && (this.mostCommonSuggestion = t2, this.suggestedVariableName = e4);
  }
  warnUnusedImports() {
    const e4 = [...this.declarations].filter(([e5, t3]) => "*" !== e5 && !t3.included && !this.reexported && !t3.referenced).map(([e5]) => e5);
    if (0 === e4.length)
      return;
    const t2 = /* @__PURE__ */ new Set();
    for (const s3 of e4)
      for (const e5 of this.declarations.get(s3).module.importers)
        t2.add(e5);
    const s2 = [...t2];
    var i2, n2, r2;
    this.options.onLog(Ae, { code: "UNUSED_EXTERNAL_IMPORT", exporter: i2 = this.id, ids: r2 = s2, message: `${Oe(n2 = e4, ["is", "are"])} imported from external module "${i2}" but never used in ${Oe(r2.map((e5) => V(e5)))}.`, names: n2 });
  }
}
const es = { ArrayPattern(e4, t2) {
  for (const s2 of t2.elements)
    s2 && es[s2.type](e4, s2);
}, AssignmentPattern(e4, t2) {
  es[t2.left.type](e4, t2.left);
}, Identifier(e4, t2) {
  e4.push(t2.name);
}, MemberExpression() {
}, ObjectPattern(e4, t2) {
  for (const s2 of t2.properties)
    "RestElement" === s2.type ? es.RestElement(e4, s2) : es[s2.value.type](e4, s2.value);
}, RestElement(e4, t2) {
  es[t2.argument.type](e4, t2.argument);
} }, ts = function(e4) {
  const t2 = [];
  return es[e4.type](t2, e4), t2;
};
function ss() {
  return { brokenFlow: false, hasBreak: false, hasContinue: false, includedCallArguments: /* @__PURE__ */ new Set(), includedLabels: /* @__PURE__ */ new Set() };
}
function is() {
  return { accessed: new te(), assigned: new te(), brokenFlow: false, called: new ie(), hasBreak: false, hasContinue: false, ignore: { breaks: false, continues: false, labels: /* @__PURE__ */ new Set(), returnYield: false, this: false }, includedLabels: /* @__PURE__ */ new Set(), instantiated: new ie(), replacedVariableInits: /* @__PURE__ */ new Map() };
}
function ns(e4, t2 = null) {
  return Object.create(t2, e4);
}
new Set("break case class catch const continue debugger default delete do else export extends finally for function if import in instanceof let new return super switch this throw try typeof var void while with yield enum await implements package protected static interface private public arguments Infinity NaN undefined null true false eval uneval isFinite isNaN parseFloat parseInt decodeURI decodeURIComponent encodeURI encodeURIComponent escape unescape Object Function Boolean Symbol Error EvalError InternalError RangeError ReferenceError SyntaxError TypeError URIError Number Math Date String RegExp Array Int8Array Uint8Array Uint8ClampedArray Int16Array Uint16Array Int32Array Uint32Array Float32Array Float64Array Map Set WeakMap WeakSet SIMD ArrayBuffer DataView JSON Promise Generator GeneratorFunction Reflect Proxy Intl".split(" ")).add("");
const rs = new class extends oe {
  getLiteralValueAtPath() {
  }
}(), os = { value: { hasEffectsWhenCalled: null, returns: ae } }, as = new class extends oe {
  getReturnExpressionWhenCalledAtPath(e4) {
    return 1 === e4.length ? bs(ms, e4[0]) : le;
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    return 0 === t2.type ? e4.length > 1 : 2 !== t2.type || 1 !== e4.length || Es(ms, e4[0], t2, s2);
  }
}(), ls = { value: { hasEffectsWhenCalled: null, returns: as } }, cs = new class extends oe {
  getReturnExpressionWhenCalledAtPath(e4) {
    return 1 === e4.length ? bs(gs, e4[0]) : le;
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    return 0 === t2.type ? e4.length > 1 : 2 !== t2.type || 1 !== e4.length || Es(gs, e4[0], t2, s2);
  }
}(), hs = { value: { hasEffectsWhenCalled: null, returns: cs } }, us = new class extends oe {
  getReturnExpressionWhenCalledAtPath(e4) {
    return 1 === e4.length ? bs(xs, e4[0]) : le;
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    return 0 === t2.type ? e4.length > 1 : 2 !== t2.type || 1 !== e4.length || Es(xs, e4[0], t2, s2);
  }
}(), ds = { value: { hasEffectsWhenCalled: null, returns: us } }, ps = { value: { hasEffectsWhenCalled({ args: e4 }, t2) {
  const s2 = e4[2];
  return e4.length < 3 || "symbol" == typeof s2.getLiteralValueAtPath(X, se, { deoptimizeCache() {
  } }) && s2.hasEffectsOnInteractionAtPath(X, de, t2);
}, returns: us } }, fs = ns({ hasOwnProperty: ls, isPrototypeOf: ls, propertyIsEnumerable: ls, toLocaleString: ds, toString: ds, valueOf: os }), ms = ns({ valueOf: ls }, fs), gs = ns({ toExponential: ds, toFixed: ds, toLocaleString: ds, toPrecision: ds, valueOf: hs }, fs), ys = ns({ exec: os, test: ls }, fs), xs = ns({ anchor: ds, at: os, big: ds, blink: ds, bold: ds, charAt: ds, charCodeAt: hs, codePointAt: os, concat: ds, endsWith: ls, fixed: ds, fontcolor: ds, fontsize: ds, includes: ls, indexOf: hs, italics: ds, lastIndexOf: hs, link: ds, localeCompare: hs, match: os, matchAll: os, normalize: ds, padEnd: ds, padStart: ds, repeat: ds, replace: ps, replaceAll: ps, search: hs, slice: ds, small: ds, split: os, startsWith: ls, strike: ds, sub: ds, substr: ds, substring: ds, sup: ds, toLocaleLowerCase: ds, toLocaleUpperCase: ds, toLowerCase: ds, toString: ds, toUpperCase: ds, trim: ds, trimEnd: ds, trimLeft: ds, trimRight: ds, trimStart: ds, valueOf: ds }, fs);
function Es(e4, t2, s2, i2) {
  var _a2, _b;
  return "string" != typeof t2 || !e4[t2] || (((_b = (_a2 = e4[t2]).hasEffectsWhenCalled) == null ? void 0 : _b.call(_a2, s2, i2)) || false);
}
function bs(e4, t2) {
  return "string" == typeof t2 && e4[t2] ? [e4[t2].returns, false] : le;
}
function vs(e4, t2, s2) {
  s2(e4, t2);
}
function Ss(e4, t2, s2) {
}
var As = {};
As.Program = As.BlockStatement = As.StaticBlock = function(e4, t2, s2) {
  for (var i2 = 0, n2 = e4.body; i2 < n2.length; i2 += 1) {
    s2(n2[i2], t2, "Statement");
  }
}, As.Statement = vs, As.EmptyStatement = Ss, As.ExpressionStatement = As.ParenthesizedExpression = As.ChainExpression = function(e4, t2, s2) {
  return s2(e4.expression, t2, "Expression");
}, As.IfStatement = function(e4, t2, s2) {
  s2(e4.test, t2, "Expression"), s2(e4.consequent, t2, "Statement"), e4.alternate && s2(e4.alternate, t2, "Statement");
}, As.LabeledStatement = function(e4, t2, s2) {
  return s2(e4.body, t2, "Statement");
}, As.BreakStatement = As.ContinueStatement = Ss, As.WithStatement = function(e4, t2, s2) {
  s2(e4.object, t2, "Expression"), s2(e4.body, t2, "Statement");
}, As.SwitchStatement = function(e4, t2, s2) {
  s2(e4.discriminant, t2, "Expression");
  for (var i2 = 0, n2 = e4.cases; i2 < n2.length; i2 += 1) {
    var r2 = n2[i2];
    r2.test && s2(r2.test, t2, "Expression");
    for (var o2 = 0, a2 = r2.consequent; o2 < a2.length; o2 += 1) {
      s2(a2[o2], t2, "Statement");
    }
  }
}, As.SwitchCase = function(e4, t2, s2) {
  e4.test && s2(e4.test, t2, "Expression");
  for (var i2 = 0, n2 = e4.consequent; i2 < n2.length; i2 += 1) {
    s2(n2[i2], t2, "Statement");
  }
}, As.ReturnStatement = As.YieldExpression = As.AwaitExpression = function(e4, t2, s2) {
  e4.argument && s2(e4.argument, t2, "Expression");
}, As.ThrowStatement = As.SpreadElement = function(e4, t2, s2) {
  return s2(e4.argument, t2, "Expression");
}, As.TryStatement = function(e4, t2, s2) {
  s2(e4.block, t2, "Statement"), e4.handler && s2(e4.handler, t2), e4.finalizer && s2(e4.finalizer, t2, "Statement");
}, As.CatchClause = function(e4, t2, s2) {
  e4.param && s2(e4.param, t2, "Pattern"), s2(e4.body, t2, "Statement");
}, As.WhileStatement = As.DoWhileStatement = function(e4, t2, s2) {
  s2(e4.test, t2, "Expression"), s2(e4.body, t2, "Statement");
}, As.ForStatement = function(e4, t2, s2) {
  e4.init && s2(e4.init, t2, "ForInit"), e4.test && s2(e4.test, t2, "Expression"), e4.update && s2(e4.update, t2, "Expression"), s2(e4.body, t2, "Statement");
}, As.ForInStatement = As.ForOfStatement = function(e4, t2, s2) {
  s2(e4.left, t2, "ForInit"), s2(e4.right, t2, "Expression"), s2(e4.body, t2, "Statement");
}, As.ForInit = function(e4, t2, s2) {
  "VariableDeclaration" === e4.type ? s2(e4, t2) : s2(e4, t2, "Expression");
}, As.DebuggerStatement = Ss, As.FunctionDeclaration = function(e4, t2, s2) {
  return s2(e4, t2, "Function");
}, As.VariableDeclaration = function(e4, t2, s2) {
  for (var i2 = 0, n2 = e4.declarations; i2 < n2.length; i2 += 1) {
    s2(n2[i2], t2);
  }
}, As.VariableDeclarator = function(e4, t2, s2) {
  s2(e4.id, t2, "Pattern"), e4.init && s2(e4.init, t2, "Expression");
}, As.Function = function(e4, t2, s2) {
  e4.id && s2(e4.id, t2, "Pattern");
  for (var i2 = 0, n2 = e4.params; i2 < n2.length; i2 += 1) {
    s2(n2[i2], t2, "Pattern");
  }
  s2(e4.body, t2, e4.expression ? "Expression" : "Statement");
}, As.Pattern = function(e4, t2, s2) {
  "Identifier" === e4.type ? s2(e4, t2, "VariablePattern") : "MemberExpression" === e4.type ? s2(e4, t2, "MemberPattern") : s2(e4, t2);
}, As.VariablePattern = Ss, As.MemberPattern = vs, As.RestElement = function(e4, t2, s2) {
  return s2(e4.argument, t2, "Pattern");
}, As.ArrayPattern = function(e4, t2, s2) {
  for (var i2 = 0, n2 = e4.elements; i2 < n2.length; i2 += 1) {
    var r2 = n2[i2];
    r2 && s2(r2, t2, "Pattern");
  }
}, As.ObjectPattern = function(e4, t2, s2) {
  for (var i2 = 0, n2 = e4.properties; i2 < n2.length; i2 += 1) {
    var r2 = n2[i2];
    "Property" === r2.type ? (r2.computed && s2(r2.key, t2, "Expression"), s2(r2.value, t2, "Pattern")) : "RestElement" === r2.type && s2(r2.argument, t2, "Pattern");
  }
}, As.Expression = vs, As.ThisExpression = As.Super = As.MetaProperty = Ss, As.ArrayExpression = function(e4, t2, s2) {
  for (var i2 = 0, n2 = e4.elements; i2 < n2.length; i2 += 1) {
    var r2 = n2[i2];
    r2 && s2(r2, t2, "Expression");
  }
}, As.ObjectExpression = function(e4, t2, s2) {
  for (var i2 = 0, n2 = e4.properties; i2 < n2.length; i2 += 1) {
    s2(n2[i2], t2);
  }
}, As.FunctionExpression = As.ArrowFunctionExpression = As.FunctionDeclaration, As.SequenceExpression = function(e4, t2, s2) {
  for (var i2 = 0, n2 = e4.expressions; i2 < n2.length; i2 += 1) {
    s2(n2[i2], t2, "Expression");
  }
}, As.TemplateLiteral = function(e4, t2, s2) {
  for (var i2 = 0, n2 = e4.quasis; i2 < n2.length; i2 += 1) {
    s2(n2[i2], t2);
  }
  for (var r2 = 0, o2 = e4.expressions; r2 < o2.length; r2 += 1) {
    s2(o2[r2], t2, "Expression");
  }
}, As.TemplateElement = Ss, As.UnaryExpression = As.UpdateExpression = function(e4, t2, s2) {
  s2(e4.argument, t2, "Expression");
}, As.BinaryExpression = As.LogicalExpression = function(e4, t2, s2) {
  s2(e4.left, t2, "Expression"), s2(e4.right, t2, "Expression");
}, As.AssignmentExpression = As.AssignmentPattern = function(e4, t2, s2) {
  s2(e4.left, t2, "Pattern"), s2(e4.right, t2, "Expression");
}, As.ConditionalExpression = function(e4, t2, s2) {
  s2(e4.test, t2, "Expression"), s2(e4.consequent, t2, "Expression"), s2(e4.alternate, t2, "Expression");
}, As.NewExpression = As.CallExpression = function(e4, t2, s2) {
  if (s2(e4.callee, t2, "Expression"), e4.arguments)
    for (var i2 = 0, n2 = e4.arguments; i2 < n2.length; i2 += 1) {
      s2(n2[i2], t2, "Expression");
    }
}, As.MemberExpression = function(e4, t2, s2) {
  s2(e4.object, t2, "Expression"), e4.computed && s2(e4.property, t2, "Expression");
}, As.ExportNamedDeclaration = As.ExportDefaultDeclaration = function(e4, t2, s2) {
  e4.declaration && s2(e4.declaration, t2, "ExportNamedDeclaration" === e4.type || e4.declaration.id ? "Statement" : "Expression"), e4.source && s2(e4.source, t2, "Expression");
}, As.ExportAllDeclaration = function(e4, t2, s2) {
  e4.exported && s2(e4.exported, t2), s2(e4.source, t2, "Expression");
}, As.ImportDeclaration = function(e4, t2, s2) {
  for (var i2 = 0, n2 = e4.specifiers; i2 < n2.length; i2 += 1) {
    s2(n2[i2], t2);
  }
  s2(e4.source, t2, "Expression");
}, As.ImportExpression = function(e4, t2, s2) {
  s2(e4.source, t2, "Expression");
}, As.ImportSpecifier = As.ImportDefaultSpecifier = As.ImportNamespaceSpecifier = As.Identifier = As.PrivateIdentifier = As.Literal = Ss, As.TaggedTemplateExpression = function(e4, t2, s2) {
  s2(e4.tag, t2, "Expression"), s2(e4.quasi, t2, "Expression");
}, As.ClassDeclaration = As.ClassExpression = function(e4, t2, s2) {
  return s2(e4, t2, "Class");
}, As.Class = function(e4, t2, s2) {
  e4.id && s2(e4.id, t2, "Pattern"), e4.superClass && s2(e4.superClass, t2, "Expression"), s2(e4.body, t2);
}, As.ClassBody = function(e4, t2, s2) {
  for (var i2 = 0, n2 = e4.body; i2 < n2.length; i2 += 1) {
    s2(n2[i2], t2);
  }
}, As.MethodDefinition = As.PropertyDefinition = As.Property = function(e4, t2, s2) {
  e4.computed && s2(e4.key, t2, "Expression"), e4.value && s2(e4.value, t2, "Expression");
};
const ks = "ArrowFunctionExpression", Is = "BinaryExpression", ws = "BlockStatement", Ps = "CallExpression", Cs = "ChainExpression", $s = "ConditionalExpression", Ns = "ExportDefaultDeclaration", _s = "ExportNamedDeclaration", Rs = "ExpressionStatement", Ms = "FunctionDeclaration", Os = "Identifier", Ds = "LogicalExpression", Ls = "NewExpression", Ts = "Program", Vs = "SequenceExpression", Bs = "VariableDeclarator", zs = "VariableDeclaration";
let Fs = "sourceMa";
Fs += "ppingURL";
const js = new RegExp(`^#[ \\f\\r\\t\\v\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff]+${Fs}=.+`), Us = "_rollupAnnotations", Gs = "_rollupRemoved";
function Ws(e4, t2, s2 = e4.type) {
  const { annotations: i2, code: n2 } = t2;
  let r2 = i2[t2.annotationIndex];
  for (; r2 && e4.start >= r2.end; )
    Ks(e4, r2, n2), r2 = i2[++t2.annotationIndex];
  if (r2 && r2.end <= e4.end)
    for (As[s2](e4, t2, Ws); (r2 = i2[t2.annotationIndex]) && r2.end <= e4.end; )
      ++t2.annotationIndex, Qs(e4, r2, false);
}
const qs = /[^\s(]/g, Hs = /\S/g;
function Ks(e4, t2, s2) {
  const i2 = [];
  let n2;
  if (Ys(s2.slice(t2.end, e4.start), qs)) {
    const t3 = e4.start;
    for (; ; ) {
      switch (i2.push(e4), e4.type) {
        case Rs:
        case Cs:
          e4 = e4.expression;
          continue;
        case Vs:
          if (Ys(s2.slice(t3, e4.start), Hs)) {
            e4 = e4.expressions[0];
            continue;
          }
          n2 = true;
          break;
        case $s:
          if (Ys(s2.slice(t3, e4.start), Hs)) {
            e4 = e4.test;
            continue;
          }
          n2 = true;
          break;
        case Ds:
        case Is:
          if (Ys(s2.slice(t3, e4.start), Hs)) {
            e4 = e4.left;
            continue;
          }
          n2 = true;
          break;
        case _s:
        case Ns:
          e4 = e4.declaration;
          continue;
        case zs: {
          const t4 = e4;
          if ("const" === t4.kind) {
            e4 = t4.declarations[0].init;
            continue;
          }
          n2 = true;
          break;
        }
        case Bs:
          e4 = e4.init;
          continue;
        case Ms:
        case ks:
        case Ps:
        case Ls:
          break;
        default:
          n2 = true;
      }
      break;
    }
  } else
    n2 = true;
  if (n2)
    Qs(e4, t2, false);
  else
    for (const e5 of i2)
      Qs(e5, t2, true);
}
function Ys(e4, t2) {
  let s2;
  for (; null !== (s2 = t2.exec(e4)); ) {
    if ("/" === s2[0]) {
      const s3 = e4.charCodeAt(t2.lastIndex);
      if (42 === s3) {
        t2.lastIndex = e4.indexOf("*/", t2.lastIndex + 1) + 2;
        continue;
      }
      if (47 === s3) {
        t2.lastIndex = e4.indexOf("\n", t2.lastIndex + 1) + 1;
        continue;
      }
    }
    return t2.lastIndex = 0, false;
  }
  return true;
}
const Xs = [["pure", /[#@]__PURE__/], ["noSideEffects", /[#@]__NO_SIDE_EFFECTS__/]];
function Qs(e4, t2, s2) {
  const i2 = s2 ? Us : Gs, n2 = e4[i2];
  n2 ? n2.push(t2) : e4[i2] = [t2];
}
const Zs = { ImportExpression: ["arguments"], Literal: [], Program: ["body"] };
const Js = "variables";
class ei extends oe {
  constructor(e4, t2, s2, i2 = false) {
    super(), this.deoptimized = false, this.esTreeNode = i2 ? e4 : null, this.keys = Zs[e4.type] || function(e5) {
      return Zs[e5.type] = Object.keys(e5).filter((t3) => "object" == typeof e5[t3] && 95 !== t3.charCodeAt(0)), Zs[e5.type];
    }(e4), this.parent = t2, this.context = t2.context, this.createScope(s2), this.parseNode(e4), this.initialise(), this.context.magicString.addSourcemapLocation(this.start), this.context.magicString.addSourcemapLocation(this.end);
  }
  addExportedVariables(e4, t2) {
  }
  bind() {
    for (const e4 of this.keys) {
      const t2 = this[e4];
      if (Array.isArray(t2))
        for (const e5 of t2)
          e5 == null ? void 0 : e5.bind();
      else
        t2 && t2.bind();
    }
  }
  createScope(e4) {
    this.scope = e4;
  }
  hasEffects(e4) {
    this.deoptimized || this.applyDeoptimizations();
    for (const t2 of this.keys) {
      const s2 = this[t2];
      if (null !== s2) {
        if (Array.isArray(s2)) {
          for (const t3 of s2)
            if (t3 == null ? void 0 : t3.hasEffects(e4))
              return true;
        } else if (s2.hasEffects(e4))
          return true;
      }
    }
    return false;
  }
  hasEffectsAsAssignmentTarget(e4, t2) {
    return this.hasEffects(e4) || this.hasEffectsOnInteractionAtPath(X, this.assignmentInteraction, e4);
  }
  include(e4, t2, s2) {
    this.deoptimized || this.applyDeoptimizations(), this.included = true;
    for (const s3 of this.keys) {
      const i2 = this[s3];
      if (null !== i2)
        if (Array.isArray(i2))
          for (const s4 of i2)
            s4 == null ? void 0 : s4.include(e4, t2);
        else
          i2.include(e4, t2);
    }
  }
  includeAsAssignmentTarget(e4, t2, s2) {
    this.include(e4, t2);
  }
  initialise() {
  }
  insertSemicolon(e4) {
    ";" !== e4.original[this.end - 1] && e4.appendLeft(this.end, ";");
  }
  parseNode(e4, t2) {
    for (const [s2, i2] of Object.entries(e4))
      if (!this.hasOwnProperty(s2))
        if (95 === s2.charCodeAt(0)) {
          if (s2 === Us) {
            const e5 = i2;
            this.annotations = e5, this.context.options.treeshake.annotations && (this.annotationNoSideEffects = e5.some((e6) => "noSideEffects" === e6.annotationType), this.annotationPure = e5.some((e6) => "pure" === e6.annotationType));
          } else if (s2 === Gs)
            for (const { start: e5, end: t3 } of i2)
              this.context.magicString.remove(e5, t3);
        } else if ("object" != typeof i2 || null === i2)
          this[s2] = i2;
        else if (Array.isArray(i2)) {
          this[s2] = [];
          for (const e5 of i2)
            this[s2].push(null === e5 ? null : new (this.context.getNodeConstructor(e5.type))(e5, this, this.scope, t2 == null ? void 0 : t2.includes(s2)));
        } else
          this[s2] = new (this.context.getNodeConstructor(i2.type))(i2, this, this.scope, t2 == null ? void 0 : t2.includes(s2));
  }
  render(e4, t2) {
    for (const s2 of this.keys) {
      const i2 = this[s2];
      if (null !== i2)
        if (Array.isArray(i2))
          for (const s3 of i2)
            s3 == null ? void 0 : s3.render(e4, t2);
        else
          i2.render(e4, t2);
    }
  }
  setAssignedValue(e4) {
    this.assignmentInteraction = { args: [null, e4], type: 1 };
  }
  shouldBeIncluded(e4) {
    return this.included || !e4.brokenFlow && this.hasEffects(is());
  }
  applyDeoptimizations() {
    this.deoptimized = true;
    for (const e4 of this.keys) {
      const t2 = this[e4];
      if (null !== t2)
        if (Array.isArray(t2))
          for (const e5 of t2)
            e5 == null ? void 0 : e5.deoptimizePath(Q);
        else
          t2.deoptimizePath(Q);
    }
    this.context.requestTreeshakingPass();
  }
}
class ti extends ei {
  deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2) {
    t2.length > 0 && this.argument.deoptimizeArgumentsOnInteractionAtPath(e4, [q, ...t2], s2);
  }
  hasEffects(e4) {
    this.deoptimized || this.applyDeoptimizations();
    const { propertyReadSideEffects: t2 } = this.context.options.treeshake;
    return this.argument.hasEffects(e4) || t2 && ("always" === t2 || this.argument.hasEffectsOnInteractionAtPath(Q, he, e4));
  }
  applyDeoptimizations() {
    this.deoptimized = true, this.argument.deoptimizePath([q, q]), this.context.requestTreeshakingPass();
  }
}
class si extends oe {
  constructor(e4) {
    super(), this.description = e4;
  }
  deoptimizeArgumentsOnInteractionAtPath({ args: e4, type: t2 }, s2) {
    var _a2;
    2 === t2 && 0 === s2.length && this.description.mutatesSelfAsArray && ((_a2 = e4[0]) == null ? void 0 : _a2.deoptimizePath(J));
  }
  getReturnExpressionWhenCalledAtPath(e4, { args: t2 }) {
    return e4.length > 0 ? le : [this.description.returnsPrimitive || ("self" === this.description.returns ? t2[0] || ae : this.description.returns()), false];
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    var _a2, _b;
    const { type: i2 } = t2;
    if (e4.length > (0 === i2 ? 1 : 0))
      return true;
    if (2 === i2) {
      const { args: e5 } = t2;
      if (true === this.description.mutatesSelfAsArray && ((_a2 = e5[0]) == null ? void 0 : _a2.hasEffectsOnInteractionAtPath(J, ue, s2)))
        return true;
      if (this.description.callsArgs) {
        for (const t3 of this.description.callsArgs)
          if ((_b = e5[t3 + 1]) == null ? void 0 : _b.hasEffectsOnInteractionAtPath(X, de, s2))
            return true;
      }
    }
    return false;
  }
}
const ii = [new si({ callsArgs: null, mutatesSelfAsArray: false, returns: null, returnsPrimitive: as })], ni = [new si({ callsArgs: null, mutatesSelfAsArray: false, returns: null, returnsPrimitive: us })], ri = [new si({ callsArgs: null, mutatesSelfAsArray: false, returns: null, returnsPrimitive: cs })], oi = [new si({ callsArgs: null, mutatesSelfAsArray: false, returns: null, returnsPrimitive: ae })], ai = /^\d+$/;
class li extends oe {
  constructor(e4, t2, s2 = false) {
    if (super(), this.prototypeExpression = t2, this.immutable = s2, this.additionalExpressionsToBeDeoptimized = /* @__PURE__ */ new Set(), this.allProperties = [], this.deoptimizedPaths = /* @__PURE__ */ Object.create(null), this.expressionsToBeDeoptimizedByKey = /* @__PURE__ */ Object.create(null), this.gettersByKey = /* @__PURE__ */ Object.create(null), this.hasLostTrack = false, this.hasUnknownDeoptimizedInteger = false, this.hasUnknownDeoptimizedProperty = false, this.propertiesAndGettersByKey = /* @__PURE__ */ Object.create(null), this.propertiesAndSettersByKey = /* @__PURE__ */ Object.create(null), this.settersByKey = /* @__PURE__ */ Object.create(null), this.unknownIntegerProps = [], this.unmatchableGetters = [], this.unmatchablePropertiesAndGetters = [], this.unmatchableSetters = [], Array.isArray(e4))
      this.buildPropertyMaps(e4);
    else {
      this.propertiesAndGettersByKey = this.propertiesAndSettersByKey = e4;
      for (const t3 of Object.values(e4))
        this.allProperties.push(...t3);
    }
  }
  deoptimizeAllProperties(e4) {
    var _a2;
    const t2 = this.hasLostTrack || this.hasUnknownDeoptimizedProperty;
    if (e4 ? this.hasUnknownDeoptimizedProperty = true : this.hasLostTrack = true, !t2) {
      for (const e5 of [...Object.values(this.propertiesAndGettersByKey), ...Object.values(this.settersByKey)])
        for (const t3 of e5)
          t3.deoptimizePath(Q);
      (_a2 = this.prototypeExpression) == null ? void 0 : _a2.deoptimizePath([q, q]), this.deoptimizeCachedEntities();
    }
  }
  deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2) {
    var _a2;
    const [i2, ...n2] = t2, { args: r2, type: o2 } = e4;
    if (this.hasLostTrack || (2 === o2 || t2.length > 1) && (this.hasUnknownDeoptimizedProperty || "string" == typeof i2 && this.deoptimizedPaths[i2]))
      return void ce(e4);
    const [a2, l2, c2] = 2 === o2 || t2.length > 1 ? [this.propertiesAndGettersByKey, this.propertiesAndGettersByKey, this.unmatchablePropertiesAndGetters] : 0 === o2 ? [this.propertiesAndGettersByKey, this.gettersByKey, this.unmatchableGetters] : [this.propertiesAndSettersByKey, this.settersByKey, this.unmatchableSetters];
    if ("string" == typeof i2) {
      if (a2[i2]) {
        const t3 = l2[i2];
        if (t3)
          for (const i3 of t3)
            i3.deoptimizeArgumentsOnInteractionAtPath(e4, n2, s2);
        if (!this.immutable)
          for (const e5 of r2)
            e5 && this.additionalExpressionsToBeDeoptimized.add(e5);
        return;
      }
      for (const t3 of c2)
        t3.deoptimizeArgumentsOnInteractionAtPath(e4, n2, s2);
      if (ai.test(i2))
        for (const t3 of this.unknownIntegerProps)
          t3.deoptimizeArgumentsOnInteractionAtPath(e4, n2, s2);
    } else {
      for (const t3 of [...Object.values(l2), c2])
        for (const i3 of t3)
          i3.deoptimizeArgumentsOnInteractionAtPath(e4, n2, s2);
      for (const t3 of this.unknownIntegerProps)
        t3.deoptimizeArgumentsOnInteractionAtPath(e4, n2, s2);
    }
    if (!this.immutable)
      for (const e5 of r2)
        e5 && this.additionalExpressionsToBeDeoptimized.add(e5);
    (_a2 = this.prototypeExpression) == null ? void 0 : _a2.deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2);
  }
  deoptimizeIntegerProperties() {
    if (!(this.hasLostTrack || this.hasUnknownDeoptimizedProperty || this.hasUnknownDeoptimizedInteger)) {
      this.hasUnknownDeoptimizedInteger = true;
      for (const [e4, t2] of Object.entries(this.propertiesAndGettersByKey))
        if (ai.test(e4))
          for (const e5 of t2)
            e5.deoptimizePath(Q);
      this.deoptimizeCachedIntegerEntities();
    }
  }
  deoptimizePath(e4) {
    var _a2;
    if (this.hasLostTrack || this.immutable)
      return;
    const t2 = e4[0];
    if (1 === e4.length) {
      if ("string" != typeof t2)
        return t2 === K ? this.deoptimizeIntegerProperties() : this.deoptimizeAllProperties(t2 === H);
      if (!this.deoptimizedPaths[t2]) {
        this.deoptimizedPaths[t2] = true;
        const e5 = this.expressionsToBeDeoptimizedByKey[t2];
        if (e5)
          for (const t3 of e5)
            t3.deoptimizeCache();
      }
    }
    const s2 = 1 === e4.length ? Q : e4.slice(1);
    for (const e5 of "string" == typeof t2 ? [...this.propertiesAndGettersByKey[t2] || this.unmatchablePropertiesAndGetters, ...this.settersByKey[t2] || this.unmatchableSetters] : this.allProperties)
      e5.deoptimizePath(s2);
    (_a2 = this.prototypeExpression) == null ? void 0 : _a2.deoptimizePath(1 === e4.length ? [...e4, q] : e4);
  }
  getLiteralValueAtPath(e4, t2, s2) {
    if (0 === e4.length)
      return re;
    const i2 = e4[0], n2 = this.getMemberExpressionAndTrackDeopt(i2, s2);
    return n2 ? n2.getLiteralValueAtPath(e4.slice(1), t2, s2) : this.prototypeExpression ? this.prototypeExpression.getLiteralValueAtPath(e4, t2, s2) : 1 !== e4.length ? ne : void 0;
  }
  getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2) {
    if (0 === e4.length)
      return le;
    const [n2, ...r2] = e4, o2 = this.getMemberExpressionAndTrackDeopt(n2, i2);
    return o2 ? o2.getReturnExpressionWhenCalledAtPath(r2, t2, s2, i2) : this.prototypeExpression ? this.prototypeExpression.getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2) : le;
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    const [i2, ...n2] = e4;
    if (n2.length > 0 || 2 === t2.type) {
      const r3 = this.getMemberExpression(i2);
      return r3 ? r3.hasEffectsOnInteractionAtPath(n2, t2, s2) : !this.prototypeExpression || this.prototypeExpression.hasEffectsOnInteractionAtPath(e4, t2, s2);
    }
    if (i2 === H)
      return false;
    if (this.hasLostTrack)
      return true;
    const [r2, o2, a2] = 0 === t2.type ? [this.propertiesAndGettersByKey, this.gettersByKey, this.unmatchableGetters] : [this.propertiesAndSettersByKey, this.settersByKey, this.unmatchableSetters];
    if ("string" == typeof i2) {
      if (r2[i2]) {
        const e5 = o2[i2];
        if (e5) {
          for (const i3 of e5)
            if (i3.hasEffectsOnInteractionAtPath(n2, t2, s2))
              return true;
        }
        return false;
      }
      for (const e5 of a2)
        if (e5.hasEffectsOnInteractionAtPath(n2, t2, s2))
          return true;
    } else
      for (const e5 of [...Object.values(o2), a2])
        for (const i3 of e5)
          if (i3.hasEffectsOnInteractionAtPath(n2, t2, s2))
            return true;
    return !!this.prototypeExpression && this.prototypeExpression.hasEffectsOnInteractionAtPath(e4, t2, s2);
  }
  buildPropertyMaps(e4) {
    const { allProperties: t2, propertiesAndGettersByKey: s2, propertiesAndSettersByKey: i2, settersByKey: n2, gettersByKey: r2, unknownIntegerProps: o2, unmatchablePropertiesAndGetters: a2, unmatchableGetters: l2, unmatchableSetters: c2 } = this, h2 = [];
    for (let u2 = e4.length - 1; u2 >= 0; u2--) {
      const { key: d2, kind: p2, property: f2 } = e4[u2];
      if (t2.push(f2), "string" == typeof d2)
        "set" === p2 ? i2[d2] || (i2[d2] = [f2, ...h2], n2[d2] = [f2, ...c2]) : "get" === p2 ? s2[d2] || (s2[d2] = [f2, ...a2], r2[d2] = [f2, ...l2]) : (i2[d2] || (i2[d2] = [f2, ...h2]), s2[d2] || (s2[d2] = [f2, ...a2]));
      else {
        if (d2 === K) {
          o2.push(f2);
          continue;
        }
        "set" === p2 && c2.push(f2), "get" === p2 && l2.push(f2), "get" !== p2 && h2.push(f2), "set" !== p2 && a2.push(f2);
      }
    }
  }
  deoptimizeCachedEntities() {
    for (const e4 of Object.values(this.expressionsToBeDeoptimizedByKey))
      for (const t2 of e4)
        t2.deoptimizeCache();
    for (const e4 of this.additionalExpressionsToBeDeoptimized)
      e4.deoptimizePath(Q);
  }
  deoptimizeCachedIntegerEntities() {
    for (const [e4, t2] of Object.entries(this.expressionsToBeDeoptimizedByKey))
      if (ai.test(e4))
        for (const e5 of t2)
          e5.deoptimizeCache();
    for (const e4 of this.additionalExpressionsToBeDeoptimized)
      e4.deoptimizePath(J);
  }
  getMemberExpression(e4) {
    if (this.hasLostTrack || this.hasUnknownDeoptimizedProperty || "string" != typeof e4 || this.hasUnknownDeoptimizedInteger && ai.test(e4) || this.deoptimizedPaths[e4])
      return ae;
    const t2 = this.propertiesAndGettersByKey[e4];
    return 1 === (t2 == null ? void 0 : t2.length) ? t2[0] : t2 || this.unmatchablePropertiesAndGetters.length > 0 || this.unknownIntegerProps.length > 0 && ai.test(e4) ? ae : null;
  }
  getMemberExpressionAndTrackDeopt(e4, t2) {
    if ("string" != typeof e4)
      return ae;
    const s2 = this.getMemberExpression(e4);
    if (s2 !== ae && !this.immutable) {
      (this.expressionsToBeDeoptimizedByKey[e4] = this.expressionsToBeDeoptimizedByKey[e4] || []).push(t2);
    }
    return s2;
  }
}
const ci = (e4) => "string" == typeof e4 && /^\d+$/.test(e4), hi = new class extends oe {
  deoptimizeArgumentsOnInteractionAtPath(e4, t2) {
    2 !== e4.type || 1 !== t2.length || ci(t2[0]) || ce(e4);
  }
  getLiteralValueAtPath(e4) {
    return 1 === e4.length && ci(e4[0]) ? void 0 : ne;
  }
  hasEffectsOnInteractionAtPath(e4, { type: t2 }) {
    return e4.length > 1 || 2 === t2;
  }
}(), ui = new li({ __proto__: null, hasOwnProperty: ii, isPrototypeOf: ii, propertyIsEnumerable: ii, toLocaleString: ni, toString: ni, valueOf: oi }, hi, true), di = [{ key: K, kind: "init", property: ae }, { key: "length", kind: "init", property: cs }], pi = [new si({ callsArgs: [0], mutatesSelfAsArray: "deopt-only", returns: null, returnsPrimitive: as })], fi = [new si({ callsArgs: [0], mutatesSelfAsArray: "deopt-only", returns: null, returnsPrimitive: cs })], mi = [new si({ callsArgs: null, mutatesSelfAsArray: true, returns: () => new li(di, ki), returnsPrimitive: null })], gi = [new si({ callsArgs: null, mutatesSelfAsArray: "deopt-only", returns: () => new li(di, ki), returnsPrimitive: null })], yi = [new si({ callsArgs: [0], mutatesSelfAsArray: "deopt-only", returns: () => new li(di, ki), returnsPrimitive: null })], xi = [new si({ callsArgs: null, mutatesSelfAsArray: true, returns: null, returnsPrimitive: cs })], Ei = [new si({ callsArgs: null, mutatesSelfAsArray: true, returns: null, returnsPrimitive: ae })], bi = [new si({ callsArgs: null, mutatesSelfAsArray: "deopt-only", returns: null, returnsPrimitive: ae })], vi = [new si({ callsArgs: [0], mutatesSelfAsArray: "deopt-only", returns: null, returnsPrimitive: ae })], Si = [new si({ callsArgs: null, mutatesSelfAsArray: true, returns: "self", returnsPrimitive: null })], Ai = [new si({ callsArgs: [0], mutatesSelfAsArray: true, returns: "self", returnsPrimitive: null })], ki = new li({ __proto__: null, at: bi, concat: gi, copyWithin: Si, entries: gi, every: pi, fill: Si, filter: yi, find: vi, findIndex: fi, findLast: vi, findLastIndex: fi, flat: gi, flatMap: yi, forEach: vi, includes: ii, indexOf: ri, join: ni, keys: oi, lastIndexOf: ri, map: yi, pop: Ei, push: xi, reduce: vi, reduceRight: vi, reverse: Si, shift: Ei, slice: gi, some: pi, sort: Ai, splice: mi, toLocaleString: ni, toString: ni, unshift: xi, values: bi }, ui, true);
class Ii extends ei {
  constructor() {
    super(...arguments), this.objectEntity = null;
  }
  deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2) {
    this.getObjectEntity().deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2);
  }
  deoptimizePath(e4) {
    this.getObjectEntity().deoptimizePath(e4);
  }
  getLiteralValueAtPath(e4, t2, s2) {
    return this.getObjectEntity().getLiteralValueAtPath(e4, t2, s2);
  }
  getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2) {
    return this.getObjectEntity().getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2);
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    return this.getObjectEntity().hasEffectsOnInteractionAtPath(e4, t2, s2);
  }
  applyDeoptimizations() {
    this.deoptimized = true;
    let e4 = false;
    for (let t2 = 0; t2 < this.elements.length; t2++) {
      const s2 = this.elements[t2];
      s2 && (e4 || s2 instanceof ti) && (e4 = true, s2.deoptimizePath(Q));
    }
    this.context.requestTreeshakingPass();
  }
  getObjectEntity() {
    if (null !== this.objectEntity)
      return this.objectEntity;
    const e4 = [{ key: "length", kind: "init", property: cs }];
    let t2 = false;
    for (let s2 = 0; s2 < this.elements.length; s2++) {
      const i2 = this.elements[s2];
      t2 || i2 instanceof ti ? i2 && (t2 = true, e4.unshift({ key: K, kind: "init", property: i2 })) : i2 ? e4.push({ key: String(s2), kind: "init", property: i2 }) : e4.push({ key: String(s2), kind: "init", property: rs });
    }
    return this.objectEntity = new li(e4, ki);
  }
}
class wi extends ei {
  addExportedVariables(e4, t2) {
    for (const s2 of this.elements)
      s2 == null ? void 0 : s2.addExportedVariables(e4, t2);
  }
  declare(e4) {
    const t2 = [];
    for (const s2 of this.elements)
      null !== s2 && t2.push(...s2.declare(e4, ae));
    return t2;
  }
  deoptimizePath() {
    for (const e4 of this.elements)
      e4 == null ? void 0 : e4.deoptimizePath(X);
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    for (const e5 of this.elements)
      if (e5 == null ? void 0 : e5.hasEffectsOnInteractionAtPath(X, t2, s2))
        return true;
    return false;
  }
  markDeclarationReached() {
    for (const e4 of this.elements)
      e4 == null ? void 0 : e4.markDeclarationReached();
  }
}
class Pi extends pe {
  constructor(e4, t2, s2, i2) {
    super(e4), this.init = s2, this.calledFromTryStatement = false, this.additionalInitializers = null, this.expressionsToBeDeoptimized = [], this.declarations = t2 ? [t2] : [], this.deoptimizationTracker = i2.deoptimizationTracker, this.module = i2.module;
  }
  addDeclaration(e4, t2) {
    this.declarations.push(e4), this.markInitializersForDeoptimization().push(t2);
  }
  consolidateInitializers() {
    if (this.additionalInitializers) {
      for (const e4 of this.additionalInitializers)
        e4.deoptimizePath(Q);
      this.additionalInitializers = null;
    }
  }
  deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2) {
    this.isReassigned ? ce(e4) : s2.withTrackedEntityAtPath(t2, this.init, () => this.init.deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2), void 0);
  }
  deoptimizePath(e4) {
    if (!this.isReassigned && !this.deoptimizationTracker.trackEntityAtPathAndGetIfTracked(e4, this))
      if (0 === e4.length) {
        if (!this.isReassigned) {
          this.isReassigned = true;
          const e5 = this.expressionsToBeDeoptimized;
          this.expressionsToBeDeoptimized = ye;
          for (const t2 of e5)
            t2.deoptimizeCache();
          this.init.deoptimizePath(Q);
        }
      } else
        this.init.deoptimizePath(e4);
  }
  getLiteralValueAtPath(e4, t2, s2) {
    return this.isReassigned ? ne : t2.withTrackedEntityAtPath(e4, this.init, () => (this.expressionsToBeDeoptimized.push(s2), this.init.getLiteralValueAtPath(e4, t2, s2)), ne);
  }
  getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2) {
    return this.isReassigned ? le : s2.withTrackedEntityAtPath(e4, this.init, () => (this.expressionsToBeDeoptimized.push(i2), this.init.getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2)), le);
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    switch (t2.type) {
      case 0:
        return !!this.isReassigned || !s2.accessed.trackEntityAtPathAndGetIfTracked(e4, this) && this.init.hasEffectsOnInteractionAtPath(e4, t2, s2);
      case 1:
        return !!this.included || 0 !== e4.length && (!!this.isReassigned || !s2.assigned.trackEntityAtPathAndGetIfTracked(e4, this) && this.init.hasEffectsOnInteractionAtPath(e4, t2, s2));
      case 2:
        return !!this.isReassigned || !(t2.withNew ? s2.instantiated : s2.called).trackEntityAtPathAndGetIfTracked(e4, t2.args, this) && this.init.hasEffectsOnInteractionAtPath(e4, t2, s2);
    }
  }
  include() {
    if (!this.included) {
      this.included = true;
      for (const e4 of this.declarations) {
        e4.included || e4.include(ss(), false);
        let t2 = e4.parent;
        for (; !t2.included && (t2.included = true, t2.type !== Ts); )
          t2 = t2.parent;
      }
    }
  }
  includeCallArguments(e4, t2) {
    if (this.isReassigned || e4.includedCallArguments.has(this.init))
      for (const s2 of t2)
        s2.include(e4, false);
    else
      e4.includedCallArguments.add(this.init), this.init.includeCallArguments(e4, t2), e4.includedCallArguments.delete(this.init);
  }
  markCalledFromTryStatement() {
    this.calledFromTryStatement = true;
  }
  markInitializersForDeoptimization() {
    return null === this.additionalInitializers && (this.additionalInitializers = [this.init], this.init = ae, this.isReassigned = true), this.additionalInitializers;
  }
  mergeDeclarations(e4) {
    const { declarations: t2 } = this;
    for (const s3 of e4.declarations)
      t2.push(s3);
    const s2 = this.markInitializersForDeoptimization();
    if (s2.push(e4.init), e4.additionalInitializers)
      for (const t3 of e4.additionalInitializers)
        s2.push(t3);
  }
}
const Ci = ye, $i = /* @__PURE__ */ new Set([q]), Ni = new te(), _i = /* @__PURE__ */ new Set([ae]);
class Ri extends Pi {
  constructor(e4, t2, s2) {
    super(e4, t2, ae, s2), this.deoptimizationInteractions = [], this.deoptimizations = new te(), this.deoptimizedFields = /* @__PURE__ */ new Set(), this.entitiesToBeDeoptimized = /* @__PURE__ */ new Set();
  }
  addEntityToBeDeoptimized(e4) {
    if (e4 === ae) {
      if (!this.entitiesToBeDeoptimized.has(ae)) {
        this.entitiesToBeDeoptimized.add(ae);
        for (const { interaction: e5 } of this.deoptimizationInteractions)
          ce(e5);
        this.deoptimizationInteractions = Ci;
      }
    } else if (this.deoptimizedFields.has(q))
      e4.deoptimizePath(Q);
    else if (!this.entitiesToBeDeoptimized.has(e4)) {
      this.entitiesToBeDeoptimized.add(e4);
      for (const t2 of this.deoptimizedFields)
        e4.deoptimizePath([t2]);
      for (const { interaction: t2, path: s2 } of this.deoptimizationInteractions)
        e4.deoptimizeArgumentsOnInteractionAtPath(t2, s2, se);
    }
  }
  deoptimizeArgumentsOnInteractionAtPath(e4, t2) {
    if (t2.length >= 2 || this.entitiesToBeDeoptimized.has(ae) || this.deoptimizationInteractions.length >= 20 || 1 === t2.length && (this.deoptimizedFields.has(q) || 2 === e4.type && this.deoptimizedFields.has(t2[0])))
      ce(e4);
    else if (!this.deoptimizations.trackEntityAtPathAndGetIfTracked(t2, e4.args)) {
      for (const s2 of this.entitiesToBeDeoptimized)
        s2.deoptimizeArgumentsOnInteractionAtPath(e4, t2, se);
      this.entitiesToBeDeoptimized.has(ae) || this.deoptimizationInteractions.push({ interaction: e4, path: t2 });
    }
  }
  deoptimizePath(e4) {
    if (0 === e4.length || this.deoptimizedFields.has(q))
      return;
    const t2 = e4[0];
    if (!this.deoptimizedFields.has(t2)) {
      this.deoptimizedFields.add(t2);
      for (const e5 of this.entitiesToBeDeoptimized)
        e5.deoptimizePath([t2]);
      t2 === q && (this.deoptimizationInteractions = Ci, this.deoptimizations = Ni, this.deoptimizedFields = $i, this.entitiesToBeDeoptimized = _i);
    }
  }
  getReturnExpressionWhenCalledAtPath(e4) {
    return 0 === e4.length ? this.deoptimizePath(Q) : this.deoptimizedFields.has(e4[0]) || this.deoptimizePath([e4[0]]), le;
  }
}
const Mi = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_$", Oi = 64;
function Di(e4) {
  let t2 = "";
  do {
    const s2 = e4 % Oi;
    e4 = e4 / Oi | 0, t2 = Mi[s2] + t2;
  } while (0 !== e4);
  return t2;
}
function Li(e4, t2, s2) {
  let i2 = e4, n2 = 1;
  for (; t2.has(i2) || Ee.has(i2) || (s2 == null ? void 0 : s2.has(i2)); )
    i2 = `${e4}$${Di(n2++)}`;
  return t2.add(i2), i2;
}
let Ti = class {
  constructor() {
    this.children = [], this.variables = /* @__PURE__ */ new Map();
  }
  addDeclaration(e4, t2, s2, i2) {
    const n2 = e4.name;
    let r2 = this.variables.get(n2);
    return r2 ? r2.addDeclaration(e4, s2) : (r2 = new Pi(e4.name, e4, s2 || rs, t2), this.variables.set(n2, r2)), r2;
  }
  contains(e4) {
    return this.variables.has(e4);
  }
  findVariable(e4) {
    throw new Error("Internal Error: findVariable needs to be implemented by a subclass");
  }
};
class Vi extends Ti {
  constructor(e4) {
    super(), this.accessedOutsideVariables = /* @__PURE__ */ new Map(), this.parent = e4, e4.children.push(this);
  }
  addAccessedDynamicImport(e4) {
    (this.accessedDynamicImports || (this.accessedDynamicImports = /* @__PURE__ */ new Set())).add(e4), this.parent instanceof Vi && this.parent.addAccessedDynamicImport(e4);
  }
  addAccessedGlobals(e4, t2) {
    const s2 = t2.get(this) || /* @__PURE__ */ new Set();
    for (const t3 of e4)
      s2.add(t3);
    t2.set(this, s2), this.parent instanceof Vi && this.parent.addAccessedGlobals(e4, t2);
  }
  addNamespaceMemberAccess(e4, t2) {
    this.accessedOutsideVariables.set(e4, t2), this.parent.addNamespaceMemberAccess(e4, t2);
  }
  addReturnExpression(e4) {
    this.parent instanceof Vi && this.parent.addReturnExpression(e4);
  }
  addUsedOutsideNames(e4, t2, s2, i2) {
    for (const i3 of this.accessedOutsideVariables.values())
      i3.included && (e4.add(i3.getBaseVariableName()), "system" === t2 && s2.has(i3) && e4.add("exports"));
    const n2 = i2.get(this);
    if (n2)
      for (const t3 of n2)
        e4.add(t3);
  }
  contains(e4) {
    return this.variables.has(e4) || this.parent.contains(e4);
  }
  deconflict(e4, t2, s2) {
    const i2 = /* @__PURE__ */ new Set();
    if (this.addUsedOutsideNames(i2, e4, t2, s2), this.accessedDynamicImports)
      for (const e5 of this.accessedDynamicImports)
        e5.inlineNamespace && i2.add(e5.inlineNamespace.getBaseVariableName());
    for (const [e5, t3] of this.variables)
      (t3.included || t3.alwaysRendered) && t3.setRenderNames(null, Li(e5, i2, t3.forbiddenNames));
    for (const i3 of this.children)
      i3.deconflict(e4, t2, s2);
  }
  findLexicalBoundary() {
    return this.parent.findLexicalBoundary();
  }
  findVariable(e4) {
    const t2 = this.variables.get(e4) || this.accessedOutsideVariables.get(e4);
    if (t2)
      return t2;
    const s2 = this.parent.findVariable(e4);
    return this.accessedOutsideVariables.set(e4, s2), s2;
  }
}
class Bi extends Vi {
  constructor(e4, t2) {
    super(e4), this.parameters = [], this.hasRest = false, this.context = t2, this.hoistedBodyVarScope = new Vi(this);
  }
  addParameterDeclaration(e4) {
    const { name: t2 } = e4, s2 = new Ri(t2, e4, this.context), i2 = this.hoistedBodyVarScope.variables.get(t2);
    return i2 && (this.hoistedBodyVarScope.variables.set(t2, s2), s2.mergeDeclarations(i2)), this.variables.set(t2, s2), s2;
  }
  addParameterVariables(e4, t2) {
    this.parameters = e4;
    for (const t3 of e4)
      for (const e5 of t3)
        e5.alwaysRendered = true;
    this.hasRest = t2;
  }
  includeCallArguments(e4, t2) {
    let s2 = false, i2 = false;
    const n2 = this.hasRest && this.parameters[this.parameters.length - 1];
    for (const s3 of t2)
      if (s3 instanceof ti) {
        for (const s4 of t2)
          s4.include(e4, false);
        break;
      }
    for (let r2 = t2.length - 1; r2 >= 0; r2--) {
      const o2 = this.parameters[r2] || n2, a2 = t2[r2];
      if (o2)
        if (s2 = false, 0 === o2.length)
          i2 = true;
        else
          for (const e5 of o2)
            e5.included && (i2 = true), e5.calledFromTryStatement && (s2 = true);
      !i2 && a2.shouldBeIncluded(e4) && (i2 = true), i2 && a2.include(e4, s2);
    }
  }
}
class zi extends Bi {
  constructor() {
    super(...arguments), this.returnExpression = null, this.returnExpressions = [];
  }
  addReturnExpression(e4) {
    this.returnExpressions.push(e4);
  }
  getReturnExpression() {
    return null === this.returnExpression && this.updateReturnExpression(), this.returnExpression;
  }
  updateReturnExpression() {
    if (1 === this.returnExpressions.length)
      this.returnExpression = this.returnExpressions[0];
    else {
      this.returnExpression = ae;
      for (const e4 of this.returnExpressions)
        e4.deoptimizePath(Q);
    }
  }
}
function Fi(e4, t2) {
  if ("MemberExpression" === e4.type)
    return !e4.computed && Fi(e4.object, e4);
  if ("Identifier" === e4.type) {
    if (!t2)
      return true;
    switch (t2.type) {
      case "MemberExpression":
        return t2.computed || e4 === t2.object;
      case "MethodDefinition":
        return t2.computed;
      case "PropertyDefinition":
      case "Property":
        return t2.computed || e4 === t2.value;
      case "ExportSpecifier":
      case "ImportSpecifier":
        return e4 === t2.local;
      case "LabeledStatement":
      case "BreakStatement":
      case "ContinueStatement":
        return false;
      default:
        return true;
    }
  }
  return false;
}
const ji = Symbol("PureFunction"), Ui = () => {
}, Gi = Symbol("Value Properties"), Wi = () => re, qi = () => false, Hi = () => true, Ki = { deoptimizeArgumentsOnCall: Ui, getLiteralValue: Wi, hasEffectsWhenCalled: qi }, Yi = { deoptimizeArgumentsOnCall: Ui, getLiteralValue: Wi, hasEffectsWhenCalled: Hi }, Xi = { __proto__: null, [Gi]: Yi }, Qi = { __proto__: null, [Gi]: Ki }, Zi = { __proto__: null, [Gi]: { deoptimizeArgumentsOnCall: Ui, getLiteralValue: Wi, hasEffectsWhenCalled({ args: e4 }, t2) {
  const [s2, i2] = e4;
  return !(i2 instanceof oe) || i2.hasEffectsOnInteractionAtPath(Q, he, t2);
} } }, Ji = { __proto__: null, [Gi]: { deoptimizeArgumentsOnCall({ args: [, e4] }) {
  e4 == null ? void 0 : e4.deoptimizePath(Q);
}, getLiteralValue: Wi, hasEffectsWhenCalled: ({ args: e4 }, t2) => e4.length <= 1 || e4[1].hasEffectsOnInteractionAtPath(Z, ue, t2) } }, en = { __proto__: null, [Gi]: Yi, prototype: Xi }, tn = { __proto__: null, [Gi]: Ki, prototype: Xi }, sn = { __proto__: null, [Gi]: { deoptimizeArgumentsOnCall: Ui, getLiteralValue: Wi, hasEffectsWhenCalled: ({ args: e4 }) => e4.length > 1 && !(e4[1] instanceof Ii) }, prototype: Xi }, nn = { __proto__: null, [Gi]: Ki, from: Xi, of: Qi, prototype: Xi }, rn = { __proto__: null, [Gi]: Ki, supportedLocalesOf: tn }, on = { global: Xi, globalThis: Xi, self: Xi, window: Xi, __proto__: null, [Gi]: Yi, Array: { __proto__: null, [Gi]: Yi, from: Xi, isArray: Qi, of: Qi, prototype: Xi }, ArrayBuffer: { __proto__: null, [Gi]: Ki, isView: Qi, prototype: Xi }, Atomics: Xi, BigInt: en, BigInt64Array: en, BigUint64Array: en, Boolean: tn, constructor: en, DataView: tn, Date: { __proto__: null, [Gi]: Ki, now: Qi, parse: Qi, prototype: Xi, UTC: Qi }, decodeURI: Qi, decodeURIComponent: Qi, encodeURI: Qi, encodeURIComponent: Qi, Error: tn, escape: Qi, eval: Xi, EvalError: tn, Float32Array: nn, Float64Array: nn, Function: en, hasOwnProperty: Xi, Infinity: Xi, Int16Array: nn, Int32Array: nn, Int8Array: nn, isFinite: Qi, isNaN: Qi, isPrototypeOf: Xi, JSON: Xi, Map: sn, Math: { __proto__: null, [Gi]: Yi, abs: Qi, acos: Qi, acosh: Qi, asin: Qi, asinh: Qi, atan: Qi, atan2: Qi, atanh: Qi, cbrt: Qi, ceil: Qi, clz32: Qi, cos: Qi, cosh: Qi, exp: Qi, expm1: Qi, floor: Qi, fround: Qi, hypot: Qi, imul: Qi, log: Qi, log10: Qi, log1p: Qi, log2: Qi, max: Qi, min: Qi, pow: Qi, random: Qi, round: Qi, sign: Qi, sin: Qi, sinh: Qi, sqrt: Qi, tan: Qi, tanh: Qi, trunc: Qi }, NaN: Xi, Number: { __proto__: null, [Gi]: Ki, isFinite: Qi, isInteger: Qi, isNaN: Qi, isSafeInteger: Qi, parseFloat: Qi, parseInt: Qi, prototype: Xi }, Object: { __proto__: null, [Gi]: Ki, create: Qi, defineProperty: Ji, defineProperties: Ji, freeze: Ji, getOwnPropertyDescriptor: Qi, getOwnPropertyDescriptors: Qi, getOwnPropertyNames: Qi, getOwnPropertySymbols: Qi, getPrototypeOf: Qi, hasOwn: Qi, is: Qi, isExtensible: Qi, isFrozen: Qi, isSealed: Qi, keys: Qi, fromEntries: Xi, entries: Zi, values: Zi, prototype: Xi }, parseFloat: Qi, parseInt: Qi, Promise: { __proto__: null, [Gi]: Yi, all: Xi, allSettled: Xi, any: Xi, prototype: Xi, race: Xi, reject: Xi, resolve: Xi }, propertyIsEnumerable: Xi, Proxy: Xi, RangeError: tn, ReferenceError: tn, Reflect: Xi, RegExp: tn, Set: sn, SharedArrayBuffer: en, String: { __proto__: null, [Gi]: Ki, fromCharCode: Qi, fromCodePoint: Qi, prototype: Xi, raw: Qi }, Symbol: { __proto__: null, [Gi]: Ki, for: Qi, keyFor: Qi, prototype: Xi, toStringTag: { __proto__: null, [Gi]: { deoptimizeArgumentsOnCall: Ui, getLiteralValue: () => Y, hasEffectsWhenCalled: Hi } } }, SyntaxError: tn, toLocaleString: Xi, toString: Xi, TypeError: tn, Uint16Array: nn, Uint32Array: nn, Uint8Array: nn, Uint8ClampedArray: nn, unescape: Qi, URIError: tn, valueOf: Xi, WeakMap: sn, WeakSet: sn, clearInterval: en, clearTimeout: en, console: { __proto__: null, [Gi]: Yi, assert: en, clear: en, count: en, countReset: en, debug: en, dir: en, dirxml: en, error: en, exception: en, group: en, groupCollapsed: en, groupEnd: en, info: en, log: en, table: en, time: en, timeEnd: en, timeLog: en, trace: en, warn: en }, Intl: { __proto__: null, [Gi]: Yi, Collator: rn, DateTimeFormat: rn, DisplayNames: rn, ListFormat: rn, Locale: rn, NumberFormat: rn, PluralRules: rn, RelativeTimeFormat: rn, Segmenter: rn }, setInterval: en, setTimeout: en, TextDecoder: en, TextEncoder: en, URL: { __proto__: null, [Gi]: Yi, prototype: Xi, canParse: Qi }, URLSearchParams: en, AbortController: en, AbortSignal: en, addEventListener: Xi, alert: Xi, AnalyserNode: en, Animation: en, AnimationEvent: en, applicationCache: Xi, ApplicationCache: en, ApplicationCacheErrorEvent: en, atob: Xi, Attr: en, Audio: en, AudioBuffer: en, AudioBufferSourceNode: en, AudioContext: en, AudioDestinationNode: en, AudioListener: en, AudioNode: en, AudioParam: en, AudioProcessingEvent: en, AudioScheduledSourceNode: en, AudioWorkletNode: en, BarProp: en, BaseAudioContext: en, BatteryManager: en, BeforeUnloadEvent: en, BiquadFilterNode: en, Blob: en, BlobEvent: en, blur: Xi, BroadcastChannel: en, btoa: Xi, ByteLengthQueuingStrategy: en, Cache: en, caches: Xi, CacheStorage: en, cancelAnimationFrame: Xi, cancelIdleCallback: Xi, CanvasCaptureMediaStreamTrack: en, CanvasGradient: en, CanvasPattern: en, CanvasRenderingContext2D: en, ChannelMergerNode: en, ChannelSplitterNode: en, CharacterData: en, clientInformation: Xi, ClipboardEvent: en, close: Xi, closed: Xi, CloseEvent: en, Comment: en, CompositionEvent: en, confirm: Xi, ConstantSourceNode: en, ConvolverNode: en, CountQueuingStrategy: en, createImageBitmap: Xi, Credential: en, CredentialsContainer: en, crypto: Xi, Crypto: en, CryptoKey: en, CSS: en, CSSConditionRule: en, CSSFontFaceRule: en, CSSGroupingRule: en, CSSImportRule: en, CSSKeyframeRule: en, CSSKeyframesRule: en, CSSMediaRule: en, CSSNamespaceRule: en, CSSPageRule: en, CSSRule: en, CSSRuleList: en, CSSStyleDeclaration: en, CSSStyleRule: en, CSSStyleSheet: en, CSSSupportsRule: en, CustomElementRegistry: en, customElements: Xi, CustomEvent: { __proto__: null, [Gi]: { deoptimizeArgumentsOnCall({ args: e4 }) {
  var _a2;
  (_a2 = e4[2]) == null ? void 0 : _a2.deoptimizePath(["detail"]);
}, getLiteralValue: Wi, hasEffectsWhenCalled: qi }, prototype: Xi }, DataTransfer: en, DataTransferItem: en, DataTransferItemList: en, defaultstatus: Xi, defaultStatus: Xi, DelayNode: en, DeviceMotionEvent: en, DeviceOrientationEvent: en, devicePixelRatio: Xi, dispatchEvent: Xi, document: Xi, Document: en, DocumentFragment: en, DocumentType: en, DOMError: en, DOMException: en, DOMImplementation: en, DOMMatrix: en, DOMMatrixReadOnly: en, DOMParser: en, DOMPoint: en, DOMPointReadOnly: en, DOMQuad: en, DOMRect: en, DOMRectReadOnly: en, DOMStringList: en, DOMStringMap: en, DOMTokenList: en, DragEvent: en, DynamicsCompressorNode: en, Element: en, ErrorEvent: en, Event: en, EventSource: en, EventTarget: en, external: Xi, fetch: Xi, File: en, FileList: en, FileReader: en, find: Xi, focus: Xi, FocusEvent: en, FontFace: en, FontFaceSetLoadEvent: en, FormData: en, frames: Xi, GainNode: en, Gamepad: en, GamepadButton: en, GamepadEvent: en, getComputedStyle: Xi, getSelection: Xi, HashChangeEvent: en, Headers: en, history: Xi, History: en, HTMLAllCollection: en, HTMLAnchorElement: en, HTMLAreaElement: en, HTMLAudioElement: en, HTMLBaseElement: en, HTMLBodyElement: en, HTMLBRElement: en, HTMLButtonElement: en, HTMLCanvasElement: en, HTMLCollection: en, HTMLContentElement: en, HTMLDataElement: en, HTMLDataListElement: en, HTMLDetailsElement: en, HTMLDialogElement: en, HTMLDirectoryElement: en, HTMLDivElement: en, HTMLDListElement: en, HTMLDocument: en, HTMLElement: en, HTMLEmbedElement: en, HTMLFieldSetElement: en, HTMLFontElement: en, HTMLFormControlsCollection: en, HTMLFormElement: en, HTMLFrameElement: en, HTMLFrameSetElement: en, HTMLHeadElement: en, HTMLHeadingElement: en, HTMLHRElement: en, HTMLHtmlElement: en, HTMLIFrameElement: en, HTMLImageElement: en, HTMLInputElement: en, HTMLLabelElement: en, HTMLLegendElement: en, HTMLLIElement: en, HTMLLinkElement: en, HTMLMapElement: en, HTMLMarqueeElement: en, HTMLMediaElement: en, HTMLMenuElement: en, HTMLMetaElement: en, HTMLMeterElement: en, HTMLModElement: en, HTMLObjectElement: en, HTMLOListElement: en, HTMLOptGroupElement: en, HTMLOptionElement: en, HTMLOptionsCollection: en, HTMLOutputElement: en, HTMLParagraphElement: en, HTMLParamElement: en, HTMLPictureElement: en, HTMLPreElement: en, HTMLProgressElement: en, HTMLQuoteElement: en, HTMLScriptElement: en, HTMLSelectElement: en, HTMLShadowElement: en, HTMLSlotElement: en, HTMLSourceElement: en, HTMLSpanElement: en, HTMLStyleElement: en, HTMLTableCaptionElement: en, HTMLTableCellElement: en, HTMLTableColElement: en, HTMLTableElement: en, HTMLTableRowElement: en, HTMLTableSectionElement: en, HTMLTemplateElement: en, HTMLTextAreaElement: en, HTMLTimeElement: en, HTMLTitleElement: en, HTMLTrackElement: en, HTMLUListElement: en, HTMLUnknownElement: en, HTMLVideoElement: en, IDBCursor: en, IDBCursorWithValue: en, IDBDatabase: en, IDBFactory: en, IDBIndex: en, IDBKeyRange: en, IDBObjectStore: en, IDBOpenDBRequest: en, IDBRequest: en, IDBTransaction: en, IDBVersionChangeEvent: en, IdleDeadline: en, IIRFilterNode: en, Image: en, ImageBitmap: en, ImageBitmapRenderingContext: en, ImageCapture: en, ImageData: en, indexedDB: Xi, innerHeight: Xi, innerWidth: Xi, InputEvent: en, IntersectionObserver: en, IntersectionObserverEntry: en, isSecureContext: Xi, KeyboardEvent: en, KeyframeEffect: en, length: Xi, localStorage: Xi, location: Xi, Location: en, locationbar: Xi, matchMedia: Xi, MediaDeviceInfo: en, MediaDevices: en, MediaElementAudioSourceNode: en, MediaEncryptedEvent: en, MediaError: en, MediaKeyMessageEvent: en, MediaKeySession: en, MediaKeyStatusMap: en, MediaKeySystemAccess: en, MediaList: en, MediaQueryList: en, MediaQueryListEvent: en, MediaRecorder: en, MediaSettingsRange: en, MediaSource: en, MediaStream: en, MediaStreamAudioDestinationNode: en, MediaStreamAudioSourceNode: en, MediaStreamEvent: en, MediaStreamTrack: en, MediaStreamTrackEvent: en, menubar: Xi, MessageChannel: en, MessageEvent: en, MessagePort: en, MIDIAccess: en, MIDIConnectionEvent: en, MIDIInput: en, MIDIInputMap: en, MIDIMessageEvent: en, MIDIOutput: en, MIDIOutputMap: en, MIDIPort: en, MimeType: en, MimeTypeArray: en, MouseEvent: en, moveBy: Xi, moveTo: Xi, MutationEvent: en, MutationObserver: en, MutationRecord: en, name: Xi, NamedNodeMap: en, NavigationPreloadManager: en, navigator: Xi, Navigator: en, NetworkInformation: en, Node: en, NodeFilter: Xi, NodeIterator: en, NodeList: en, Notification: en, OfflineAudioCompletionEvent: en, OfflineAudioContext: en, offscreenBuffering: Xi, OffscreenCanvas: en, open: Xi, openDatabase: Xi, Option: en, origin: Xi, OscillatorNode: en, outerHeight: Xi, outerWidth: Xi, PageTransitionEvent: en, pageXOffset: Xi, pageYOffset: Xi, PannerNode: en, parent: Xi, Path2D: en, PaymentAddress: en, PaymentRequest: en, PaymentRequestUpdateEvent: en, PaymentResponse: en, performance: Xi, Performance: en, PerformanceEntry: en, PerformanceLongTaskTiming: en, PerformanceMark: en, PerformanceMeasure: en, PerformanceNavigation: en, PerformanceNavigationTiming: en, PerformanceObserver: en, PerformanceObserverEntryList: en, PerformancePaintTiming: en, PerformanceResourceTiming: en, PerformanceTiming: en, PeriodicWave: en, Permissions: en, PermissionStatus: en, personalbar: Xi, PhotoCapabilities: en, Plugin: en, PluginArray: en, PointerEvent: en, PopStateEvent: en, postMessage: Xi, Presentation: en, PresentationAvailability: en, PresentationConnection: en, PresentationConnectionAvailableEvent: en, PresentationConnectionCloseEvent: en, PresentationConnectionList: en, PresentationReceiver: en, PresentationRequest: en, print: Xi, ProcessingInstruction: en, ProgressEvent: en, PromiseRejectionEvent: en, prompt: Xi, PushManager: en, PushSubscription: en, PushSubscriptionOptions: en, queueMicrotask: Xi, RadioNodeList: en, Range: en, ReadableStream: en, RemotePlayback: en, removeEventListener: Xi, Request: en, requestAnimationFrame: Xi, requestIdleCallback: Xi, resizeBy: Xi, ResizeObserver: en, ResizeObserverEntry: en, resizeTo: Xi, Response: en, RTCCertificate: en, RTCDataChannel: en, RTCDataChannelEvent: en, RTCDtlsTransport: en, RTCIceCandidate: en, RTCIceTransport: en, RTCPeerConnection: en, RTCPeerConnectionIceEvent: en, RTCRtpReceiver: en, RTCRtpSender: en, RTCSctpTransport: en, RTCSessionDescription: en, RTCStatsReport: en, RTCTrackEvent: en, screen: Xi, Screen: en, screenLeft: Xi, ScreenOrientation: en, screenTop: Xi, screenX: Xi, screenY: Xi, ScriptProcessorNode: en, scroll: Xi, scrollbars: Xi, scrollBy: Xi, scrollTo: Xi, scrollX: Xi, scrollY: Xi, SecurityPolicyViolationEvent: en, Selection: en, ServiceWorker: en, ServiceWorkerContainer: en, ServiceWorkerRegistration: en, sessionStorage: Xi, ShadowRoot: en, SharedWorker: en, SourceBuffer: en, SourceBufferList: en, speechSynthesis: Xi, SpeechSynthesisEvent: en, SpeechSynthesisUtterance: en, StaticRange: en, status: Xi, statusbar: Xi, StereoPannerNode: en, stop: Xi, Storage: en, StorageEvent: en, StorageManager: en, styleMedia: Xi, StyleSheet: en, StyleSheetList: en, SubtleCrypto: en, SVGAElement: en, SVGAngle: en, SVGAnimatedAngle: en, SVGAnimatedBoolean: en, SVGAnimatedEnumeration: en, SVGAnimatedInteger: en, SVGAnimatedLength: en, SVGAnimatedLengthList: en, SVGAnimatedNumber: en, SVGAnimatedNumberList: en, SVGAnimatedPreserveAspectRatio: en, SVGAnimatedRect: en, SVGAnimatedString: en, SVGAnimatedTransformList: en, SVGAnimateElement: en, SVGAnimateMotionElement: en, SVGAnimateTransformElement: en, SVGAnimationElement: en, SVGCircleElement: en, SVGClipPathElement: en, SVGComponentTransferFunctionElement: en, SVGDefsElement: en, SVGDescElement: en, SVGDiscardElement: en, SVGElement: en, SVGEllipseElement: en, SVGFEBlendElement: en, SVGFEColorMatrixElement: en, SVGFEComponentTransferElement: en, SVGFECompositeElement: en, SVGFEConvolveMatrixElement: en, SVGFEDiffuseLightingElement: en, SVGFEDisplacementMapElement: en, SVGFEDistantLightElement: en, SVGFEDropShadowElement: en, SVGFEFloodElement: en, SVGFEFuncAElement: en, SVGFEFuncBElement: en, SVGFEFuncGElement: en, SVGFEFuncRElement: en, SVGFEGaussianBlurElement: en, SVGFEImageElement: en, SVGFEMergeElement: en, SVGFEMergeNodeElement: en, SVGFEMorphologyElement: en, SVGFEOffsetElement: en, SVGFEPointLightElement: en, SVGFESpecularLightingElement: en, SVGFESpotLightElement: en, SVGFETileElement: en, SVGFETurbulenceElement: en, SVGFilterElement: en, SVGForeignObjectElement: en, SVGGElement: en, SVGGeometryElement: en, SVGGradientElement: en, SVGGraphicsElement: en, SVGImageElement: en, SVGLength: en, SVGLengthList: en, SVGLinearGradientElement: en, SVGLineElement: en, SVGMarkerElement: en, SVGMaskElement: en, SVGMatrix: en, SVGMetadataElement: en, SVGMPathElement: en, SVGNumber: en, SVGNumberList: en, SVGPathElement: en, SVGPatternElement: en, SVGPoint: en, SVGPointList: en, SVGPolygonElement: en, SVGPolylineElement: en, SVGPreserveAspectRatio: en, SVGRadialGradientElement: en, SVGRect: en, SVGRectElement: en, SVGScriptElement: en, SVGSetElement: en, SVGStopElement: en, SVGStringList: en, SVGStyleElement: en, SVGSVGElement: en, SVGSwitchElement: en, SVGSymbolElement: en, SVGTextContentElement: en, SVGTextElement: en, SVGTextPathElement: en, SVGTextPositioningElement: en, SVGTitleElement: en, SVGTransform: en, SVGTransformList: en, SVGTSpanElement: en, SVGUnitTypes: en, SVGUseElement: en, SVGViewElement: en, TaskAttributionTiming: en, Text: en, TextEvent: en, TextMetrics: en, TextTrack: en, TextTrackCue: en, TextTrackCueList: en, TextTrackList: en, TimeRanges: en, toolbar: Xi, top: Xi, Touch: en, TouchEvent: en, TouchList: en, TrackEvent: en, TransitionEvent: en, TreeWalker: en, UIEvent: en, ValidityState: en, visualViewport: Xi, VisualViewport: en, VTTCue: en, WaveShaperNode: en, WebAssembly: Xi, WebGL2RenderingContext: en, WebGLActiveInfo: en, WebGLBuffer: en, WebGLContextEvent: en, WebGLFramebuffer: en, WebGLProgram: en, WebGLQuery: en, WebGLRenderbuffer: en, WebGLRenderingContext: en, WebGLSampler: en, WebGLShader: en, WebGLShaderPrecisionFormat: en, WebGLSync: en, WebGLTexture: en, WebGLTransformFeedback: en, WebGLUniformLocation: en, WebGLVertexArrayObject: en, WebSocket: en, WheelEvent: en, Window: en, Worker: en, WritableStream: en, XMLDocument: en, XMLHttpRequest: en, XMLHttpRequestEventTarget: en, XMLHttpRequestUpload: en, XMLSerializer: en, XPathEvaluator: en, XPathExpression: en, XPathResult: en, XSLTProcessor: en };
for (const e4 of ["window", "global", "self", "globalThis"])
  on[e4] = on;
function an(e4) {
  let t2 = on;
  for (const s2 of e4) {
    if ("string" != typeof s2)
      return null;
    if (t2 = t2[s2], !t2)
      return null;
  }
  return t2[Gi];
}
class ln extends pe {
  constructor() {
    super(...arguments), this.isReassigned = true;
  }
  deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2) {
    switch (e4.type) {
      case 0:
      case 1:
        return void (an([this.name, ...t2].slice(0, -1)) || super.deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2));
      case 2: {
        const i2 = an([this.name, ...t2]);
        return void (i2 ? i2.deoptimizeArgumentsOnCall(e4) : super.deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2));
      }
    }
  }
  getLiteralValueAtPath(e4, t2, s2) {
    const i2 = an([this.name, ...e4]);
    return i2 ? i2.getLiteralValue() : ne;
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    switch (t2.type) {
      case 0:
        return 0 === e4.length ? "undefined" !== this.name && !an([this.name]) : !an([this.name, ...e4].slice(0, -1));
      case 1:
        return true;
      case 2: {
        const i2 = an([this.name, ...e4]);
        return !i2 || i2.hasEffectsWhenCalled(t2, s2);
      }
    }
  }
}
const cn = { __proto__: null, class: true, const: true, let: true, var: true };
class hn extends ei {
  constructor() {
    super(...arguments), this.variable = null, this.isTDZAccess = null;
  }
  addExportedVariables(e4, t2) {
    t2.has(this.variable) && e4.push(this.variable);
  }
  bind() {
    !this.variable && Fi(this, this.parent) && (this.variable = this.scope.findVariable(this.name), this.variable.addReference(this));
  }
  declare(e4, t2) {
    let s2;
    const { treeshake: i2 } = this.context.options;
    switch (e4) {
      case "var":
        s2 = this.scope.addDeclaration(this, this.context, t2, true), i2 && i2.correctVarValueBeforeDeclaration && s2.markInitializersForDeoptimization();
        break;
      case "function":
      case "let":
      case "const":
      case "class":
        s2 = this.scope.addDeclaration(this, this.context, t2, false);
        break;
      case "parameter":
        s2 = this.scope.addParameterDeclaration(this);
        break;
      default:
        throw new Error(`Internal Error: Unexpected identifier kind ${e4}.`);
    }
    return s2.kind = e4, [this.variable = s2];
  }
  deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2) {
    this.variable.deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2);
  }
  deoptimizePath(e4) {
    var _a2;
    0 !== e4.length || this.scope.contains(this.name) || this.disallowImportReassignment(), (_a2 = this.variable) == null ? void 0 : _a2.deoptimizePath(e4);
  }
  getLiteralValueAtPath(e4, t2, s2) {
    return this.getVariableRespectingTDZ().getLiteralValueAtPath(e4, t2, s2);
  }
  getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2) {
    const [n2, r2] = this.getVariableRespectingTDZ().getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2);
    return [n2, r2 || this.isPureFunction(e4)];
  }
  hasEffects(e4) {
    return this.deoptimized || this.applyDeoptimizations(), !(!this.isPossibleTDZ() || "var" === this.variable.kind) || this.context.options.treeshake.unknownGlobalSideEffects && this.variable instanceof ln && !this.isPureFunction(X) && this.variable.hasEffectsOnInteractionAtPath(X, he, e4);
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    switch (t2.type) {
      case 0:
        return null !== this.variable && !this.isPureFunction(e4) && this.getVariableRespectingTDZ().hasEffectsOnInteractionAtPath(e4, t2, s2);
      case 1:
        return (e4.length > 0 ? this.getVariableRespectingTDZ() : this.variable).hasEffectsOnInteractionAtPath(e4, t2, s2);
      case 2:
        return !this.isPureFunction(e4) && this.getVariableRespectingTDZ().hasEffectsOnInteractionAtPath(e4, t2, s2);
    }
  }
  include() {
    this.deoptimized || this.applyDeoptimizations(), this.included || (this.included = true, null !== this.variable && this.context.includeVariableInModule(this.variable));
  }
  includeCallArguments(e4, t2) {
    this.variable.includeCallArguments(e4, t2);
  }
  isPossibleTDZ() {
    if (null !== this.isTDZAccess)
      return this.isTDZAccess;
    if (!(this.variable instanceof Pi && this.variable.kind && this.variable.kind in cn && this.variable.module === this.context.module))
      return this.isTDZAccess = false;
    let e4;
    return this.variable.declarations && 1 === this.variable.declarations.length && (e4 = this.variable.declarations[0]) && this.start < e4.start && un(this) === un(e4) ? this.isTDZAccess = true : this.variable.initReached ? this.isTDZAccess = false : this.isTDZAccess = true;
  }
  markDeclarationReached() {
    this.variable.initReached = true;
  }
  render(e4, { snippets: { getPropertyAccess: t2 }, useOriginalName: s2 }, { renderedParentType: i2, isCalleeOfRenderedParent: n2, isShorthandProperty: r2 } = me) {
    if (this.variable) {
      const o2 = this.variable.getName(t2, s2);
      o2 !== this.name && (e4.overwrite(this.start, this.end, o2, { contentOnly: true, storeName: true }), r2 && e4.prependRight(this.start, `${this.name}: `)), "eval" === o2 && i2 === Ps && n2 && e4.appendRight(this.start, "0, ");
    }
  }
  applyDeoptimizations() {
    this.deoptimized = true, this.variable instanceof Pi && (this.variable.consolidateInitializers(), this.context.requestTreeshakingPass());
  }
  disallowImportReassignment() {
    return this.context.error(Vt(this.name, this.context.module.id), this.start);
  }
  getVariableRespectingTDZ() {
    return this.isPossibleTDZ() ? ae : this.variable;
  }
  isPureFunction(e4) {
    let t2 = this.context.manualPureFunctions[this.name];
    for (const s2 of e4) {
      if (!t2)
        return false;
      if (t2[ji])
        return true;
      t2 = t2[s2];
    }
    return t2 == null ? void 0 : t2[ji];
  }
}
function un(e4) {
  for (; e4 && !/^Program|Function/.test(e4.type); )
    e4 = e4.parent;
  return e4;
}
function dn(e4, t2, s2, i2) {
  if (t2.remove(s2, i2), e4.annotations)
    for (const i3 of e4.annotations) {
      if (!(i3.start < s2))
        return;
      t2.remove(i3.start, i3.end);
    }
}
function pn(e4, t2) {
  if (e4.annotations || e4.parent.type !== Rs || (e4 = e4.parent), e4.annotations)
    for (const s2 of e4.annotations)
      t2.remove(s2.start, s2.end);
}
const fn = { isNoStatement: true };
function mn(e4, t2, s2 = 0) {
  let i2, n2;
  for (i2 = e4.indexOf(t2, s2); ; ) {
    if (-1 === (s2 = e4.indexOf("/", s2)) || s2 >= i2)
      return i2;
    n2 = e4.charCodeAt(++s2), ++s2, (s2 = 47 === n2 ? e4.indexOf("\n", s2) + 1 : e4.indexOf("*/", s2) + 2) > i2 && (i2 = e4.indexOf(t2, s2));
  }
}
const gn = /\S/g;
function yn(e4, t2) {
  gn.lastIndex = t2;
  return gn.exec(e4).index;
}
function xn(e4) {
  let t2, s2, i2 = 0;
  for (t2 = e4.indexOf("\n", i2); ; ) {
    if (i2 = e4.indexOf("/", i2), -1 === i2 || i2 > t2)
      return [t2, t2 + 1];
    if (s2 = e4.charCodeAt(i2 + 1), 47 === s2)
      return [i2, t2 + 1];
    i2 = e4.indexOf("*/", i2 + 3) + 2, i2 > t2 && (t2 = e4.indexOf("\n", i2));
  }
}
function En(e4, t2, s2, i2, n2) {
  let r2, o2, a2, l2, c2 = e4[0], h2 = !c2.included || c2.needsBoundaries;
  h2 && (l2 = s2 + xn(t2.original.slice(s2, c2.start))[1]);
  for (let s3 = 1; s3 <= e4.length; s3++)
    r2 = c2, o2 = l2, a2 = h2, c2 = e4[s3], h2 = void 0 !== c2 && (!c2.included || c2.needsBoundaries), a2 || h2 ? (l2 = r2.end + xn(t2.original.slice(r2.end, void 0 === c2 ? i2 : c2.start))[1], r2.included ? a2 ? r2.render(t2, n2, { end: l2, start: o2 }) : r2.render(t2, n2) : dn(r2, t2, o2, l2)) : r2.render(t2, n2);
}
function bn(e4, t2, s2, i2) {
  const n2 = [];
  let r2, o2, a2, l2, c2 = s2 - 1;
  for (const i3 of e4) {
    for (void 0 !== r2 && (c2 = r2.end + mn(t2.original.slice(r2.end, i3.start), ",")), o2 = a2 = c2 + 1 + xn(t2.original.slice(c2 + 1, i3.start))[1]; l2 = t2.original.charCodeAt(o2), 32 === l2 || 9 === l2 || 10 === l2 || 13 === l2; )
      o2++;
    void 0 !== r2 && n2.push({ contentEnd: a2, end: o2, node: r2, separator: c2, start: s2 }), r2 = i3, s2 = o2;
  }
  return n2.push({ contentEnd: i2, end: i2, node: r2, separator: null, start: s2 }), n2;
}
function vn(e4, t2, s2) {
  for (; ; ) {
    const [i2, n2] = xn(e4.original.slice(t2, s2));
    if (-1 === i2)
      break;
    e4.remove(t2 + i2, t2 += n2);
  }
}
class Sn extends Vi {
  addDeclaration(e4, t2, s2, i2) {
    if (i2) {
      const n2 = this.parent.addDeclaration(e4, t2, s2, i2);
      return n2.markInitializersForDeoptimization(), n2;
    }
    return super.addDeclaration(e4, t2, s2, false);
  }
}
class An extends ei {
  initialise() {
    var e4, t2;
    this.directive && "use strict" !== this.directive && this.parent.type === Ts && this.context.log(Ae, (e4 = this.directive, { code: "MODULE_LEVEL_DIRECTIVE", id: t2 = this.context.module.id, message: `Module level directives cause errors when bundled, "${e4}" in "${V(t2)}" was ignored.` }), this.start);
  }
  render(e4, t2) {
    super.render(e4, t2), this.included && this.insertSemicolon(e4);
  }
  shouldBeIncluded(e4) {
    return this.directive && "use strict" !== this.directive ? this.parent.type !== Ts : super.shouldBeIncluded(e4);
  }
  applyDeoptimizations() {
  }
}
class kn extends ei {
  constructor() {
    super(...arguments), this.directlyIncluded = false;
  }
  addImplicitReturnExpressionToScope() {
    const e4 = this.body[this.body.length - 1];
    e4 && "ReturnStatement" === e4.type || this.scope.addReturnExpression(ae);
  }
  createScope(e4) {
    this.scope = this.parent.preventChildBlockScope ? e4 : new Sn(e4);
  }
  hasEffects(e4) {
    if (this.deoptimizeBody)
      return true;
    for (const t2 of this.body) {
      if (e4.brokenFlow)
        break;
      if (t2.hasEffects(e4))
        return true;
    }
    return false;
  }
  include(e4, t2) {
    if (!this.deoptimizeBody || !this.directlyIncluded) {
      this.included = true, this.directlyIncluded = true, this.deoptimizeBody && (t2 = true);
      for (const s2 of this.body)
        (t2 || s2.shouldBeIncluded(e4)) && s2.include(e4, t2);
    }
  }
  initialise() {
    const e4 = this.body[0];
    this.deoptimizeBody = e4 instanceof An && "use asm" === e4.directive;
  }
  render(e4, t2) {
    this.body.length > 0 ? En(this.body, e4, this.start + 1, this.end - 1, t2) : super.render(e4, t2);
  }
}
class In extends ei {
  constructor() {
    super(...arguments), this.declarationInit = null;
  }
  addExportedVariables(e4, t2) {
    this.argument.addExportedVariables(e4, t2);
  }
  declare(e4, t2) {
    return this.declarationInit = t2, this.argument.declare(e4, ae);
  }
  deoptimizePath(e4) {
    0 === e4.length && this.argument.deoptimizePath(X);
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    return e4.length > 0 || this.argument.hasEffectsOnInteractionAtPath(X, t2, s2);
  }
  markDeclarationReached() {
    this.argument.markDeclarationReached();
  }
  applyDeoptimizations() {
    this.deoptimized = true, null !== this.declarationInit && (this.declarationInit.deoptimizePath([q, q]), this.context.requestTreeshakingPass());
  }
}
class wn extends ei {
  constructor() {
    super(...arguments), this.objectEntity = null, this.deoptimizedReturn = false;
  }
  deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2) {
    if (2 === e4.type) {
      const { parameters: t3 } = this.scope, { args: s3 } = e4;
      let i2 = false;
      for (let e5 = 0; e5 < s3.length - 1; e5++) {
        const n2 = this.params[e5], r2 = s3[e5 + 1];
        i2 || n2 instanceof In ? (i2 = true, r2.deoptimizePath(Q)) : n2 instanceof hn ? (t3[e5][0].addEntityToBeDeoptimized(r2), this.addArgumentToBeDeoptimized(r2)) : n2 ? r2.deoptimizePath(Q) : this.addArgumentToBeDeoptimized(r2);
      }
    } else
      this.getObjectEntity().deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2);
  }
  deoptimizePath(e4) {
    if (this.getObjectEntity().deoptimizePath(e4), 1 === e4.length && e4[0] === q) {
      this.scope.getReturnExpression().deoptimizePath(Q);
      for (const e5 of this.scope.parameters)
        for (const t2 of e5)
          t2.deoptimizePath(Q);
    }
  }
  getLiteralValueAtPath(e4, t2, s2) {
    return this.getObjectEntity().getLiteralValueAtPath(e4, t2, s2);
  }
  getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2) {
    return e4.length > 0 ? this.getObjectEntity().getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2) : this.async ? (this.deoptimizedReturn || (this.deoptimizedReturn = true, this.scope.getReturnExpression().deoptimizePath(Q), this.context.requestTreeshakingPass()), le) : [this.scope.getReturnExpression(), false];
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    if (e4.length > 0 || 2 !== t2.type)
      return this.getObjectEntity().hasEffectsOnInteractionAtPath(e4, t2, s2);
    if (this.annotationNoSideEffects)
      return false;
    if (this.async) {
      const { propertyReadSideEffects: e5 } = this.context.options.treeshake, t3 = this.scope.getReturnExpression();
      if (t3.hasEffectsOnInteractionAtPath(["then"], de, s2) || e5 && ("always" === e5 || t3.hasEffectsOnInteractionAtPath(["then"], he, s2)))
        return true;
    }
    for (const e5 of this.params)
      if (e5.hasEffects(s2))
        return true;
    return false;
  }
  include(e4, t2) {
    this.deoptimized || this.applyDeoptimizations(), this.included = true;
    const { brokenFlow: s2 } = e4;
    e4.brokenFlow = false, this.body.include(e4, t2), e4.brokenFlow = s2;
  }
  includeCallArguments(e4, t2) {
    this.scope.includeCallArguments(e4, t2);
  }
  initialise() {
    this.scope.addParameterVariables(this.params.map((e4) => e4.declare("parameter", ae)), this.params[this.params.length - 1] instanceof In), this.body instanceof kn ? this.body.addImplicitReturnExpressionToScope() : this.scope.addReturnExpression(this.body);
  }
  parseNode(e4) {
    e4.body.type === ws && (this.body = new kn(e4.body, this, this.scope.hoistedBodyVarScope)), super.parseNode(e4);
  }
  addArgumentToBeDeoptimized(e4) {
  }
  applyDeoptimizations() {
  }
}
wn.prototype.preventChildBlockScope = true;
class Pn extends wn {
  constructor() {
    super(...arguments), this.objectEntity = null;
  }
  createScope(e4) {
    this.scope = new zi(e4, this.context);
  }
  hasEffects() {
    return this.deoptimized || this.applyDeoptimizations(), false;
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    if (super.hasEffectsOnInteractionAtPath(e4, t2, s2))
      return true;
    if (this.annotationNoSideEffects)
      return false;
    if (2 === t2.type) {
      const { ignore: e5, brokenFlow: t3 } = s2;
      if (s2.ignore = { breaks: false, continues: false, labels: /* @__PURE__ */ new Set(), returnYield: true, this: false }, this.body.hasEffects(s2))
        return true;
      s2.ignore = e5, s2.brokenFlow = t3;
    }
    return false;
  }
  include(e4, t2) {
    super.include(e4, t2);
    for (const s2 of this.params)
      s2 instanceof hn || s2.include(e4, t2);
  }
  getObjectEntity() {
    return null !== this.objectEntity ? this.objectEntity : this.objectEntity = new li([], ui);
  }
}
function Cn(e4, { exportNamesByVariable: t2, snippets: { _: s2, getObject: i2, getPropertyAccess: n2 } }, r2 = "") {
  if (1 === e4.length && 1 === t2.get(e4[0]).length) {
    const i3 = e4[0];
    return `exports('${t2.get(i3)}',${s2}${i3.getName(n2)}${r2})`;
  }
  {
    const s3 = [];
    for (const i3 of e4)
      for (const e5 of t2.get(i3))
        s3.push([e5, i3.getName(n2) + r2]);
    return `exports(${i2(s3, { lineBreakIndent: null })})`;
  }
}
function $n(e4, t2, s2, i2, { exportNamesByVariable: n2, snippets: { _: r2 } }) {
  i2.prependRight(t2, `exports('${n2.get(e4)}',${r2}`), i2.appendLeft(s2, ")");
}
function Nn(e4, t2, s2, i2, n2, r2) {
  const { _: o2, getPropertyAccess: a2 } = r2.snippets;
  n2.appendLeft(s2, `,${o2}${Cn([e4], r2)},${o2}${e4.getName(a2)}`), i2 && (n2.prependRight(t2, "("), n2.appendLeft(s2, ")"));
}
class _n extends ei {
  addExportedVariables(e4, t2) {
    for (const s2 of this.properties)
      "Property" === s2.type ? s2.value.addExportedVariables(e4, t2) : s2.argument.addExportedVariables(e4, t2);
  }
  declare(e4, t2) {
    const s2 = [];
    for (const i2 of this.properties)
      s2.push(...i2.declare(e4, t2));
    return s2;
  }
  deoptimizePath(e4) {
    if (0 === e4.length)
      for (const t2 of this.properties)
        t2.deoptimizePath(e4);
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    for (const e5 of this.properties)
      if (e5.hasEffectsOnInteractionAtPath(X, t2, s2))
        return true;
    return false;
  }
  markDeclarationReached() {
    for (const e4 of this.properties)
      e4.markDeclarationReached();
  }
}
class Rn extends Pi {
  constructor(e4) {
    super("arguments", null, ae, e4), this.deoptimizedArguments = [];
  }
  addArgumentToBeDeoptimized(e4) {
    this.included ? e4.deoptimizePath(Q) : this.deoptimizedArguments.push(e4);
  }
  hasEffectsOnInteractionAtPath(e4, { type: t2 }) {
    return 0 !== t2 || e4.length > 1;
  }
  include() {
    super.include();
    for (const e4 of this.deoptimizedArguments)
      e4.deoptimizePath(Q);
    this.deoptimizedArguments.length = 0;
  }
}
class Mn extends Ri {
  constructor(e4) {
    super("this", null, e4);
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    return (s2.replacedVariableInits.get(this) || ae).hasEffectsOnInteractionAtPath(e4, t2, s2);
  }
}
class On extends zi {
  constructor(e4, t2) {
    super(e4, t2), this.variables.set("arguments", this.argumentsVariable = new Rn(t2)), this.variables.set("this", this.thisVariable = new Mn(t2));
  }
  findLexicalBoundary() {
    return this;
  }
  includeCallArguments(e4, t2) {
    if (super.includeCallArguments(e4, t2), this.argumentsVariable.included)
      for (const s2 of t2)
        s2.included || s2.include(e4, false);
  }
}
class Dn extends wn {
  constructor() {
    super(...arguments), this.objectEntity = null;
  }
  createScope(e4) {
    this.scope = new On(e4, this.context), this.constructedEntity = new li(/* @__PURE__ */ Object.create(null), ui), this.scope.thisVariable.addEntityToBeDeoptimized(this.constructedEntity);
  }
  deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2) {
    super.deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2), 2 === e4.type && 0 === t2.length && e4.args[0] && this.scope.thisVariable.addEntityToBeDeoptimized(e4.args[0]);
  }
  hasEffects(e4) {
    var _a2;
    return this.deoptimized || this.applyDeoptimizations(), !this.annotationNoSideEffects && !!((_a2 = this.id) == null ? void 0 : _a2.hasEffects(e4));
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    if (super.hasEffectsOnInteractionAtPath(e4, t2, s2))
      return true;
    if (this.annotationNoSideEffects)
      return false;
    if (2 === t2.type) {
      const e5 = s2.replacedVariableInits.get(this.scope.thisVariable);
      s2.replacedVariableInits.set(this.scope.thisVariable, t2.withNew ? this.constructedEntity : ae);
      const { brokenFlow: i2, ignore: n2, replacedVariableInits: r2 } = s2;
      if (s2.ignore = { breaks: false, continues: false, labels: /* @__PURE__ */ new Set(), returnYield: true, this: t2.withNew }, this.body.hasEffects(s2))
        return true;
      s2.brokenFlow = i2, e5 ? r2.set(this.scope.thisVariable, e5) : r2.delete(this.scope.thisVariable), s2.ignore = n2;
    }
    return false;
  }
  include(e4, t2) {
    var _a2;
    super.include(e4, t2), (_a2 = this.id) == null ? void 0 : _a2.include();
    const s2 = this.scope.argumentsVariable.included;
    for (const i2 of this.params)
      i2 instanceof hn && !s2 || i2.include(e4, t2);
  }
  initialise() {
    var _a2;
    super.initialise(), (_a2 = this.id) == null ? void 0 : _a2.declare("function", this);
  }
  addArgumentToBeDeoptimized(e4) {
    this.scope.argumentsVariable.addArgumentToBeDeoptimized(e4);
  }
  getObjectEntity() {
    return null !== this.objectEntity ? this.objectEntity : this.objectEntity = new li([{ key: "prototype", kind: "init", property: new li([], ui) }], ui);
  }
}
class Ln extends ei {
  hasEffects() {
    return this.deoptimized || this.applyDeoptimizations(), true;
  }
  include(e4, t2) {
    if (this.deoptimized || this.applyDeoptimizations(), !this.included) {
      this.included = true;
      e:
        if (!this.context.usesTopLevelAwait) {
          let e5 = this.parent;
          do {
            if (e5 instanceof Dn || e5 instanceof Pn)
              break e;
          } while (e5 = e5.parent);
          this.context.usesTopLevelAwait = true;
        }
    }
    this.argument.include(e4, t2);
  }
}
const Tn = { "!=": (e4, t2) => e4 != t2, "!==": (e4, t2) => e4 !== t2, "%": (e4, t2) => e4 % t2, "&": (e4, t2) => e4 & t2, "*": (e4, t2) => e4 * t2, "**": (e4, t2) => e4 ** t2, "+": (e4, t2) => e4 + t2, "-": (e4, t2) => e4 - t2, "/": (e4, t2) => e4 / t2, "<": (e4, t2) => e4 < t2, "<<": (e4, t2) => e4 << t2, "<=": (e4, t2) => e4 <= t2, "==": (e4, t2) => e4 == t2, "===": (e4, t2) => e4 === t2, ">": (e4, t2) => e4 > t2, ">=": (e4, t2) => e4 >= t2, ">>": (e4, t2) => e4 >> t2, ">>>": (e4, t2) => e4 >>> t2, "^": (e4, t2) => e4 ^ t2, "|": (e4, t2) => e4 | t2 };
function Vn(e4, t2, s2) {
  if (s2.arguments.length > 0)
    if (s2.arguments[s2.arguments.length - 1].included)
      for (const i2 of s2.arguments)
        i2.render(e4, t2);
    else {
      let i2 = s2.arguments.length - 2;
      for (; i2 >= 0 && !s2.arguments[i2].included; )
        i2--;
      if (i2 >= 0) {
        for (let n2 = 0; n2 <= i2; n2++)
          s2.arguments[n2].render(e4, t2);
        e4.remove(mn(e4.original, ",", s2.arguments[i2].end), s2.end - 1);
      } else
        e4.remove(mn(e4.original, "(", s2.callee.end) + 1, s2.end - 1);
    }
}
class Bn extends ei {
  deoptimizeArgumentsOnInteractionAtPath() {
  }
  getLiteralValueAtPath(e4) {
    return e4.length > 0 || null === this.value && 110 !== this.context.code.charCodeAt(this.start) || "bigint" == typeof this.value || 47 === this.context.code.charCodeAt(this.start) ? ne : this.value;
  }
  getReturnExpressionWhenCalledAtPath(e4) {
    return 1 !== e4.length ? le : bs(this.members, e4[0]);
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    switch (t2.type) {
      case 0:
        return e4.length > (null === this.value ? 0 : 1);
      case 1:
        return true;
      case 2:
        return !!(this.included && this.value instanceof RegExp && (this.value.global || this.value.sticky)) || (1 !== e4.length || Es(this.members, e4[0], t2, s2));
    }
  }
  initialise() {
    this.members = function(e4) {
      if (e4 instanceof RegExp)
        return ys;
      switch (typeof e4) {
        case "boolean":
          return ms;
        case "number":
          return gs;
        case "string":
          return xs;
      }
      return /* @__PURE__ */ Object.create(null);
    }(this.value);
  }
  parseNode(e4) {
    this.value = e4.value, this.regex = e4.regex, super.parseNode(e4);
  }
  render(e4) {
    "string" == typeof this.value && e4.indentExclusionRanges.push([this.start + 1, this.end - 1]);
  }
}
function zn(e4) {
  return e4.computed ? function(e5) {
    if (e5 instanceof Bn)
      return String(e5.value);
    return null;
  }(e4.property) : e4.property.name;
}
function Fn(e4) {
  const t2 = e4.propertyKey, s2 = e4.object;
  if ("string" == typeof t2) {
    if (s2 instanceof hn)
      return [{ key: s2.name, pos: s2.start }, { key: t2, pos: e4.property.start }];
    if (s2 instanceof jn) {
      const i2 = Fn(s2);
      return i2 && [...i2, { key: t2, pos: e4.property.start }];
    }
  }
  return null;
}
class jn extends ei {
  constructor() {
    super(...arguments), this.variable = null, this.assignmentDeoptimized = false, this.bound = false, this.expressionsToBeDeoptimized = [], this.isUndefined = false;
  }
  bind() {
    this.bound = true;
    const e4 = Fn(this), t2 = e4 && this.scope.findVariable(e4[0].key);
    if (t2 == null ? void 0 : t2.isNamespace) {
      const s2 = Un(t2, e4.slice(1), this.context);
      s2 ? "undefined" === s2 ? this.isUndefined = true : (this.variable = s2, this.scope.addNamespaceMemberAccess(function(e5) {
        let t3 = e5[0].key;
        for (let s3 = 1; s3 < e5.length; s3++)
          t3 += "." + e5[s3].key;
        return t3;
      }(e4), s2)) : super.bind();
    } else
      super.bind();
  }
  deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2) {
    this.variable ? this.variable.deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2) : this.isUndefined || (t2.length < 7 ? this.object.deoptimizeArgumentsOnInteractionAtPath(e4, [this.getPropertyKey(), ...t2], s2) : ce(e4));
  }
  deoptimizeCache() {
    const { expressionsToBeDeoptimized: e4, object: t2 } = this;
    this.expressionsToBeDeoptimized = ye, this.propertyKey = q, t2.deoptimizePath(Q);
    for (const t3 of e4)
      t3.deoptimizeCache();
  }
  deoptimizePath(e4) {
    if (0 === e4.length && this.disallowNamespaceReassignment(), this.variable)
      this.variable.deoptimizePath(e4);
    else if (!this.isUndefined && e4.length < 7) {
      const t2 = this.getPropertyKey();
      this.object.deoptimizePath([t2 === q ? H : t2, ...e4]);
    }
  }
  getLiteralValueAtPath(e4, t2, s2) {
    return this.variable ? this.variable.getLiteralValueAtPath(e4, t2, s2) : this.isUndefined ? void 0 : this.propertyKey !== q && e4.length < 7 ? (this.expressionsToBeDeoptimized.push(s2), this.object.getLiteralValueAtPath([this.getPropertyKey(), ...e4], t2, s2)) : ne;
  }
  getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2) {
    return this.variable ? this.variable.getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2) : this.isUndefined ? [rs, false] : this.propertyKey !== q && e4.length < 7 ? (this.expressionsToBeDeoptimized.push(i2), this.object.getReturnExpressionWhenCalledAtPath([this.getPropertyKey(), ...e4], t2, s2, i2)) : le;
  }
  hasEffects(e4) {
    return this.deoptimized || this.applyDeoptimizations(), this.property.hasEffects(e4) || this.object.hasEffects(e4) || this.hasAccessEffect(e4);
  }
  hasEffectsAsAssignmentTarget(e4, t2) {
    return t2 && !this.deoptimized && this.applyDeoptimizations(), this.assignmentDeoptimized || this.applyAssignmentDeoptimization(), this.property.hasEffects(e4) || this.object.hasEffects(e4) || t2 && this.hasAccessEffect(e4) || this.hasEffectsOnInteractionAtPath(X, this.assignmentInteraction, e4);
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    return this.variable ? this.variable.hasEffectsOnInteractionAtPath(e4, t2, s2) : !!this.isUndefined || (!(e4.length < 7) || this.object.hasEffectsOnInteractionAtPath([this.getPropertyKey(), ...e4], t2, s2));
  }
  include(e4, t2) {
    this.deoptimized || this.applyDeoptimizations(), this.includeProperties(e4, t2);
  }
  includeAsAssignmentTarget(e4, t2, s2) {
    this.assignmentDeoptimized || this.applyAssignmentDeoptimization(), s2 ? this.include(e4, t2) : this.includeProperties(e4, t2);
  }
  includeCallArguments(e4, t2) {
    this.variable ? this.variable.includeCallArguments(e4, t2) : super.includeCallArguments(e4, t2);
  }
  initialise() {
    this.propertyKey = zn(this), this.accessInteraction = { args: [this.object], type: 0 };
  }
  isSkippedAsOptional(e4) {
    var _a2, _b;
    return !this.variable && !this.isUndefined && (((_b = (_a2 = this.object).isSkippedAsOptional) == null ? void 0 : _b.call(_a2, e4)) || this.optional && null == this.object.getLiteralValueAtPath(X, se, e4));
  }
  render(e4, t2, { renderedParentType: s2, isCalleeOfRenderedParent: i2, renderedSurroundingElement: n2 } = me) {
    if (this.variable || this.isUndefined) {
      const { snippets: { getPropertyAccess: n3 } } = t2;
      let r2 = this.variable ? this.variable.getName(n3) : "undefined";
      s2 && i2 && (r2 = "0, " + r2), e4.overwrite(this.start, this.end, r2, { contentOnly: true, storeName: true });
    } else
      s2 && i2 && e4.appendRight(this.start, "0, "), this.object.render(e4, t2, { renderedSurroundingElement: n2 }), this.property.render(e4, t2);
  }
  setAssignedValue(e4) {
    this.assignmentInteraction = { args: [this.object, e4], type: 1 };
  }
  applyDeoptimizations() {
    this.deoptimized = true;
    const { propertyReadSideEffects: e4 } = this.context.options.treeshake;
    if (this.bound && e4 && !this.variable && !this.isUndefined) {
      const e5 = this.getPropertyKey();
      this.object.deoptimizeArgumentsOnInteractionAtPath(this.accessInteraction, [e5], se), this.context.requestTreeshakingPass();
    }
  }
  applyAssignmentDeoptimization() {
    this.assignmentDeoptimized = true;
    const { propertyReadSideEffects: e4 } = this.context.options.treeshake;
    this.bound && e4 && !this.variable && !this.isUndefined && (this.object.deoptimizeArgumentsOnInteractionAtPath(this.assignmentInteraction, [this.getPropertyKey()], se), this.context.requestTreeshakingPass());
  }
  disallowNamespaceReassignment() {
    if (this.object instanceof hn) {
      this.scope.findVariable(this.object.name).isNamespace && (this.variable && this.context.includeVariableInModule(this.variable), this.context.log(Ae, Vt(this.object.name, this.context.module.id), this.start));
    }
  }
  getPropertyKey() {
    if (null === this.propertyKey) {
      this.propertyKey = q;
      const e4 = this.property.getLiteralValueAtPath(X, se, this);
      return this.propertyKey = e4 === Y ? e4 : "symbol" == typeof e4 ? q : String(e4);
    }
    return this.propertyKey;
  }
  hasAccessEffect(e4) {
    const { propertyReadSideEffects: t2 } = this.context.options.treeshake;
    return !(this.variable || this.isUndefined) && t2 && ("always" === t2 || this.object.hasEffectsOnInteractionAtPath([this.getPropertyKey()], this.accessInteraction, e4));
  }
  includeProperties(e4, t2) {
    this.included || (this.included = true, this.variable && this.context.includeVariableInModule(this.variable)), this.object.include(e4, t2), this.property.include(e4, t2);
  }
}
function Un(e4, t2, s2) {
  if (0 === t2.length)
    return e4;
  if (!e4.isNamespace || e4 instanceof fe)
    return null;
  const i2 = t2[0].key, n2 = e4.context.traceExport(i2);
  if (!n2) {
    if (1 === t2.length) {
      const n3 = e4.context.fileName;
      return s2.log(Ae, Ut(i2, s2.module.id, n3), t2[0].pos), "undefined";
    }
    return null;
  }
  return Un(n2, t2.slice(1), s2);
}
class Gn extends ei {
  constructor() {
    super(...arguments), this.returnExpression = null, this.deoptimizableDependentExpressions = [], this.expressionsToBeDeoptimized = /* @__PURE__ */ new Set();
  }
  deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2) {
    const { args: i2 } = e4, [n2, r2] = this.getReturnExpression(s2);
    if (r2)
      return;
    const o2 = i2.filter((e5) => !!e5 && e5 !== ae);
    if (0 !== o2.length)
      if (n2 === ae)
        for (const e5 of o2)
          e5.deoptimizePath(Q);
      else
        s2.withTrackedEntityAtPath(t2, n2, () => {
          for (const e5 of o2)
            this.expressionsToBeDeoptimized.add(e5);
          n2.deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2);
        }, null);
  }
  deoptimizeCache() {
    var _a2;
    if (((_a2 = this.returnExpression) == null ? void 0 : _a2[0]) !== ae) {
      this.returnExpression = le;
      const { deoptimizableDependentExpressions: e4, expressionsToBeDeoptimized: t2 } = this;
      this.expressionsToBeDeoptimized = xe, this.deoptimizableDependentExpressions = ye;
      for (const t3 of e4)
        t3.deoptimizeCache();
      for (const e5 of t2)
        e5.deoptimizePath(Q);
    }
  }
  deoptimizePath(e4) {
    if (0 === e4.length || this.context.deoptimizationTracker.trackEntityAtPathAndGetIfTracked(e4, this))
      return;
    const [t2] = this.getReturnExpression();
    t2 !== ae && t2.deoptimizePath(e4);
  }
  getLiteralValueAtPath(e4, t2, s2) {
    const [i2] = this.getReturnExpression(t2);
    return i2 === ae ? ne : t2.withTrackedEntityAtPath(e4, i2, () => (this.deoptimizableDependentExpressions.push(s2), i2.getLiteralValueAtPath(e4, t2, s2)), ne);
  }
  getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2) {
    const n2 = this.getReturnExpression(s2);
    return n2[0] === ae ? n2 : s2.withTrackedEntityAtPath(e4, n2, () => {
      this.deoptimizableDependentExpressions.push(i2);
      const [r2, o2] = n2[0].getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2);
      return [r2, o2 || n2[1]];
    }, le);
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    const { type: i2 } = t2;
    if (2 === i2) {
      const { args: i3, withNew: n3 } = t2;
      if ((n3 ? s2.instantiated : s2.called).trackEntityAtPathAndGetIfTracked(e4, i3, this))
        return false;
    } else if ((1 === i2 ? s2.assigned : s2.accessed).trackEntityAtPathAndGetIfTracked(e4, this))
      return false;
    const [n2, r2] = this.getReturnExpression();
    return (1 === i2 || !r2) && n2.hasEffectsOnInteractionAtPath(e4, t2, s2);
  }
}
class Wn extends Gn {
  bind() {
    if (super.bind(), this.callee instanceof hn) {
      this.scope.findVariable(this.callee.name).isNamespace && this.context.log(Ae, Ot(this.callee.name), this.start), "eval" === this.callee.name && this.context.log(Ae, { code: "EVAL", id: e4 = this.context.module.id, message: `Use of eval in "${V(e4)}" is strongly discouraged as it poses security risks and may cause issues with minification.`, url: De("troubleshooting/#avoiding-eval") }, this.start);
    }
    var e4;
    this.interaction = { args: [this.callee instanceof jn && !this.callee.variable ? this.callee.object : null, ...this.arguments], type: 2, withNew: false };
  }
  hasEffects(e4) {
    try {
      for (const t2 of this.arguments)
        if (t2.hasEffects(e4))
          return true;
      return !this.annotationPure && (this.callee.hasEffects(e4) || this.callee.hasEffectsOnInteractionAtPath(X, this.interaction, e4));
    } finally {
      this.deoptimized || this.applyDeoptimizations();
    }
  }
  include(e4, t2) {
    this.deoptimized || this.applyDeoptimizations(), t2 ? (super.include(e4, t2), t2 === Js && this.callee instanceof hn && this.callee.variable && this.callee.variable.markCalledFromTryStatement()) : (this.included = true, this.callee.include(e4, false)), this.callee.includeCallArguments(e4, this.arguments);
  }
  isSkippedAsOptional(e4) {
    var _a2, _b;
    return ((_b = (_a2 = this.callee).isSkippedAsOptional) == null ? void 0 : _b.call(_a2, e4)) || this.optional && null == this.callee.getLiteralValueAtPath(X, se, e4);
  }
  render(e4, t2, { renderedSurroundingElement: s2 } = me) {
    this.callee.render(e4, t2, { isCalleeOfRenderedParent: true, renderedSurroundingElement: s2 }), Vn(e4, t2, this);
  }
  applyDeoptimizations() {
    this.deoptimized = true, this.callee.deoptimizeArgumentsOnInteractionAtPath(this.interaction, X, se), this.context.requestTreeshakingPass();
  }
  getReturnExpression(e4 = se) {
    return null === this.returnExpression ? (this.returnExpression = le, this.returnExpression = this.callee.getReturnExpressionWhenCalledAtPath(X, this.interaction, e4, this)) : this.returnExpression;
  }
}
class qn extends Bi {
  addDeclaration(e4, t2, s2, i2) {
    const n2 = this.variables.get(e4.name);
    return n2 ? (this.parent.addDeclaration(e4, t2, rs, i2), n2.addDeclaration(e4, s2), n2) : this.parent.addDeclaration(e4, t2, s2, i2);
  }
}
class Hn extends Vi {
  constructor(e4, t2, s2) {
    super(e4), this.variables.set("this", this.thisVariable = new Pi("this", null, t2, s2)), this.instanceScope = new Vi(this), this.instanceScope.variables.set("this", new Mn(s2));
  }
  findLexicalBoundary() {
    return this;
  }
}
class Kn extends ei {
  constructor() {
    super(...arguments), this.accessedValue = null;
  }
  deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2) {
    return 0 === e4.type && "get" === this.kind && 0 === t2.length || 1 === e4.type && "set" === this.kind && 0 === t2.length ? this.value.deoptimizeArgumentsOnInteractionAtPath({ args: e4.args, type: 2, withNew: false }, X, s2) : void this.getAccessedValue()[0].deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2);
  }
  deoptimizeCache() {
  }
  deoptimizePath(e4) {
    this.getAccessedValue()[0].deoptimizePath(e4);
  }
  getLiteralValueAtPath(e4, t2, s2) {
    return this.getAccessedValue()[0].getLiteralValueAtPath(e4, t2, s2);
  }
  getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2) {
    return this.getAccessedValue()[0].getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2);
  }
  hasEffects(e4) {
    return this.key.hasEffects(e4);
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    return "get" === this.kind && 0 === t2.type && 0 === e4.length || "set" === this.kind && 1 === t2.type ? this.value.hasEffectsOnInteractionAtPath(X, { args: t2.args, type: 2, withNew: false }, s2) : this.getAccessedValue()[0].hasEffectsOnInteractionAtPath(e4, t2, s2);
  }
  applyDeoptimizations() {
  }
  getAccessedValue() {
    return null === this.accessedValue ? "get" === this.kind ? (this.accessedValue = le, this.accessedValue = this.value.getReturnExpressionWhenCalledAtPath(X, de, se, this)) : this.accessedValue = [this.value, false] : this.accessedValue;
  }
}
class Yn extends Kn {
  applyDeoptimizations() {
  }
}
class Xn extends oe {
  constructor(e4, t2) {
    super(), this.object = e4, this.key = t2;
  }
  deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2) {
    this.object.deoptimizeArgumentsOnInteractionAtPath(e4, [this.key, ...t2], s2);
  }
  deoptimizePath(e4) {
    this.object.deoptimizePath([this.key, ...e4]);
  }
  getLiteralValueAtPath(e4, t2, s2) {
    return this.object.getLiteralValueAtPath([this.key, ...e4], t2, s2);
  }
  getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2) {
    return this.object.getReturnExpressionWhenCalledAtPath([this.key, ...e4], t2, s2, i2);
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    return this.object.hasEffectsOnInteractionAtPath([this.key, ...e4], t2, s2);
  }
}
class Qn extends ei {
  constructor() {
    super(...arguments), this.objectEntity = null;
  }
  createScope(e4) {
    this.scope = new Vi(e4);
  }
  deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2) {
    this.getObjectEntity().deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2);
  }
  deoptimizeCache() {
    this.getObjectEntity().deoptimizeAllProperties();
  }
  deoptimizePath(e4) {
    this.getObjectEntity().deoptimizePath(e4);
  }
  getLiteralValueAtPath(e4, t2, s2) {
    return this.getObjectEntity().getLiteralValueAtPath(e4, t2, s2);
  }
  getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2) {
    return this.getObjectEntity().getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2);
  }
  hasEffects(e4) {
    var _a2, _b;
    this.deoptimized || this.applyDeoptimizations();
    const t2 = ((_a2 = this.superClass) == null ? void 0 : _a2.hasEffects(e4)) || this.body.hasEffects(e4);
    return (_b = this.id) == null ? void 0 : _b.markDeclarationReached(), t2 || super.hasEffects(e4);
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    var _a2;
    return 2 === t2.type && 0 === e4.length ? !t2.withNew || (null === this.classConstructor ? (_a2 = this.superClass) == null ? void 0 : _a2.hasEffectsOnInteractionAtPath(e4, t2, s2) : this.classConstructor.hasEffectsOnInteractionAtPath(e4, t2, s2)) || false : this.getObjectEntity().hasEffectsOnInteractionAtPath(e4, t2, s2);
  }
  include(e4, t2) {
    var _a2;
    this.deoptimized || this.applyDeoptimizations(), this.included = true, (_a2 = this.superClass) == null ? void 0 : _a2.include(e4, t2), this.body.include(e4, t2), this.id && (this.id.markDeclarationReached(), this.id.include());
  }
  initialise() {
    var _a2;
    (_a2 = this.id) == null ? void 0 : _a2.declare("class", this);
    for (const e4 of this.body.body)
      if (e4 instanceof Yn && "constructor" === e4.kind)
        return void (this.classConstructor = e4);
    this.classConstructor = null;
  }
  applyDeoptimizations() {
    this.deoptimized = true;
    for (const e4 of this.body.body)
      e4.static || e4 instanceof Yn && "constructor" === e4.kind || e4.deoptimizePath(Q);
    this.context.requestTreeshakingPass();
  }
  getObjectEntity() {
    if (null !== this.objectEntity)
      return this.objectEntity;
    const e4 = [], t2 = [];
    for (const s2 of this.body.body) {
      const i2 = s2.static ? e4 : t2, n2 = s2.kind;
      if (i2 === t2 && !n2)
        continue;
      const r2 = "set" === n2 || "get" === n2 ? n2 : "init";
      let o2;
      if (s2.computed) {
        const e5 = s2.key.getLiteralValueAtPath(X, se, this);
        if ("symbol" == typeof e5) {
          i2.push({ key: q, kind: r2, property: s2 });
          continue;
        }
        o2 = String(e5);
      } else
        o2 = s2.key instanceof hn ? s2.key.name : String(s2.key.value);
      i2.push({ key: o2, kind: r2, property: s2 });
    }
    return e4.unshift({ key: "prototype", kind: "init", property: new li(t2, this.superClass ? new Xn(this.superClass, "prototype") : ui) }), this.objectEntity = new li(e4, this.superClass || ui);
  }
}
class Zn extends Qn {
  initialise() {
    super.initialise(), null !== this.id && (this.id.variable.isId = true);
  }
  parseNode(e4) {
    null !== e4.id && (this.id = new hn(e4.id, this, this.scope.parent)), super.parseNode(e4);
  }
  render(e4, t2) {
    var _a2;
    const { exportNamesByVariable: s2, format: i2, snippets: { _: n2, getPropertyAccess: r2 } } = t2;
    if (this.id) {
      const { variable: o2, name: a2 } = this.id;
      "system" === i2 && s2.has(o2) && e4.appendLeft(this.end, `${n2}${Cn([o2], t2)};`);
      const l2 = o2.getName(r2);
      if (l2 !== a2)
        return (_a2 = this.superClass) == null ? void 0 : _a2.render(e4, t2), this.body.render(e4, { ...t2, useOriginalName: (e5) => e5 === o2 }), e4.prependRight(this.start, `let ${l2}${n2}=${n2}`), void e4.prependLeft(this.end, ";");
    }
    super.render(e4, t2);
  }
  applyDeoptimizations() {
    super.applyDeoptimizations();
    const { id: e4, scope: t2 } = this;
    if (e4) {
      const { name: s2, variable: i2 } = e4;
      for (const e5 of t2.accessedOutsideVariables.values())
        e5 !== i2 && e5.forbidName(s2);
    }
  }
}
class Jn extends Qn {
  render(e4, t2, { renderedSurroundingElement: s2 } = me) {
    super.render(e4, t2), s2 === Rs && (e4.appendRight(this.start, "("), e4.prependLeft(this.end, ")"));
  }
}
class er extends oe {
  constructor(e4) {
    super(), this.expressions = e4, this.included = false;
  }
  deoptimizePath(e4) {
    for (const t2 of this.expressions)
      t2.deoptimizePath(e4);
  }
  getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2) {
    return [new er(this.expressions.map((n2) => n2.getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2)[0])), false];
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    for (const i2 of this.expressions)
      if (i2.hasEffectsOnInteractionAtPath(e4, t2, s2))
        return true;
    return false;
  }
}
function tr(e4, t2) {
  const { brokenFlow: s2, hasBreak: i2, hasContinue: n2, ignore: r2 } = e4, { breaks: o2, continues: a2 } = r2;
  return r2.breaks = true, r2.continues = true, e4.hasBreak = false, e4.hasContinue = false, !!t2.hasEffects(e4) || (r2.breaks = o2, r2.continues = a2, e4.hasBreak = i2, e4.hasContinue = n2, e4.brokenFlow = s2, false);
}
function sr(e4, t2, s2) {
  const { brokenFlow: i2, hasBreak: n2, hasContinue: r2 } = e4;
  e4.hasBreak = false, e4.hasContinue = false, t2.include(e4, s2, { asSingleStatement: true }), e4.hasBreak = n2, e4.hasContinue = r2, e4.brokenFlow = i2;
}
class ir extends ei {
  hasEffects() {
    return false;
  }
  initialise() {
    this.context.addExport(this);
  }
  render(e4, t2, s2) {
    e4.remove(s2.start, s2.end);
  }
  applyDeoptimizations() {
  }
}
ir.prototype.needsBoundaries = true;
class nr extends Dn {
  initialise() {
    super.initialise(), null !== this.id && (this.id.variable.isId = true);
  }
  parseNode(e4) {
    null !== e4.id && (this.id = new hn(e4.id, this, this.scope.parent)), super.parseNode(e4);
  }
}
class rr extends ei {
  include(e4, t2) {
    super.include(e4, t2), t2 && this.context.includeVariableInModule(this.variable);
  }
  initialise() {
    const e4 = this.declaration;
    this.declarationName = e4.id && e4.id.name || this.declaration.name, this.variable = this.scope.addExportDefaultDeclaration(this.declarationName || this.context.getModuleName(), this, this.context), this.context.addExport(this);
  }
  render(e4, t2, s2) {
    const { start: i2, end: n2 } = s2, r2 = function(e5, t3) {
      return yn(e5, mn(e5, "default", t3) + 7);
    }(e4.original, this.start);
    if (this.declaration instanceof nr)
      this.renderNamedDeclaration(e4, r2, null === this.declaration.id ? function(e5, t3) {
        const s3 = mn(e5, "function", t3) + 8;
        e5 = e5.slice(s3, mn(e5, "(", s3));
        const i3 = mn(e5, "*");
        return -1 === i3 ? s3 : s3 + i3 + 1;
      }(e4.original, r2) : null, t2);
    else if (this.declaration instanceof Zn)
      this.renderNamedDeclaration(e4, r2, null === this.declaration.id ? mn(e4.original, "class", i2) + 5 : null, t2);
    else {
      if (this.variable.getOriginalVariable() !== this.variable)
        return void dn(this, e4, i2, n2);
      if (!this.variable.included)
        return e4.remove(this.start, r2), this.declaration.render(e4, t2, { renderedSurroundingElement: Rs }), void (";" !== e4.original[this.end - 1] && e4.appendLeft(this.end, ";"));
      this.renderVariableDeclaration(e4, r2, t2);
    }
    this.declaration.render(e4, t2);
  }
  applyDeoptimizations() {
  }
  renderNamedDeclaration(e4, t2, s2, i2) {
    const { exportNamesByVariable: n2, format: r2, snippets: { getPropertyAccess: o2 } } = i2, a2 = this.variable.getName(o2);
    e4.remove(this.start, t2), null !== s2 && e4.appendLeft(s2, ` ${a2}`), "system" === r2 && this.declaration instanceof Zn && n2.has(this.variable) && e4.appendLeft(this.end, ` ${Cn([this.variable], i2)};`);
  }
  renderVariableDeclaration(e4, t2, { format: s2, exportNamesByVariable: i2, snippets: { cnst: n2, getPropertyAccess: r2 } }) {
    const o2 = 59 === e4.original.charCodeAt(this.end - 1), a2 = "system" === s2 && i2.get(this.variable);
    a2 ? (e4.overwrite(this.start, t2, `${n2} ${this.variable.getName(r2)} = exports('${a2[0]}', `), e4.appendRight(o2 ? this.end - 1 : this.end, ")" + (o2 ? "" : ";"))) : (e4.overwrite(this.start, t2, `${n2} ${this.variable.getName(r2)} = `), o2 || e4.appendLeft(this.end, ";"));
  }
}
rr.prototype.needsBoundaries = true;
class or extends ei {
  bind() {
    var _a2;
    (_a2 = this.declaration) == null ? void 0 : _a2.bind();
  }
  hasEffects(e4) {
    var _a2;
    return !!((_a2 = this.declaration) == null ? void 0 : _a2.hasEffects(e4));
  }
  initialise() {
    this.context.addExport(this);
  }
  render(e4, t2, s2) {
    const { start: i2, end: n2 } = s2;
    null === this.declaration ? e4.remove(i2, n2) : (e4.remove(this.start, this.declaration.start), this.declaration.render(e4, t2, { end: n2, start: i2 }));
  }
  applyDeoptimizations() {
  }
}
or.prototype.needsBoundaries = true;
class ar extends Dn {
  render(e4, t2, { renderedSurroundingElement: s2 } = me) {
    super.render(e4, t2), s2 === Rs && (e4.appendRight(this.start, "("), e4.prependLeft(this.end, ")"));
  }
}
class lr extends Sn {
  constructor() {
    super(...arguments), this.hoistedDeclarations = [];
  }
  addDeclaration(e4, t2, s2, i2) {
    return this.hoistedDeclarations.push(e4), super.addDeclaration(e4, t2, s2, i2);
  }
}
const cr = Symbol("unset");
class hr extends ei {
  constructor() {
    super(...arguments), this.testValue = cr;
  }
  deoptimizeCache() {
    this.testValue = ne;
  }
  hasEffects(e4) {
    var _a2;
    if (this.test.hasEffects(e4))
      return true;
    const t2 = this.getTestValue();
    if ("symbol" == typeof t2) {
      const { brokenFlow: t3 } = e4;
      if (this.consequent.hasEffects(e4))
        return true;
      const s2 = e4.brokenFlow;
      return e4.brokenFlow = t3, null === this.alternate ? false : !!this.alternate.hasEffects(e4) || (e4.brokenFlow = e4.brokenFlow && s2, false);
    }
    return t2 ? this.consequent.hasEffects(e4) : !!((_a2 = this.alternate) == null ? void 0 : _a2.hasEffects(e4));
  }
  include(e4, t2) {
    if (this.included = true, t2)
      this.includeRecursively(t2, e4);
    else {
      const t3 = this.getTestValue();
      "symbol" == typeof t3 ? this.includeUnknownTest(e4) : this.includeKnownTest(e4, t3);
    }
  }
  parseNode(e4) {
    this.consequentScope = new lr(this.scope), this.consequent = new (this.context.getNodeConstructor(e4.consequent.type))(e4.consequent, this, this.consequentScope), e4.alternate && (this.alternateScope = new lr(this.scope), this.alternate = new (this.context.getNodeConstructor(e4.alternate.type))(e4.alternate, this, this.alternateScope)), super.parseNode(e4);
  }
  render(e4, t2) {
    const { snippets: { getPropertyAccess: s2 } } = t2, i2 = this.getTestValue(), n2 = [], r2 = this.test.included, o2 = !this.context.options.treeshake;
    r2 ? this.test.render(e4, t2) : e4.remove(this.start, this.consequent.start), this.consequent.included && (o2 || "symbol" == typeof i2 || i2) ? this.consequent.render(e4, t2) : (e4.overwrite(this.consequent.start, this.consequent.end, r2 ? ";" : ""), n2.push(...this.consequentScope.hoistedDeclarations)), this.alternate && (!this.alternate.included || !o2 && "symbol" != typeof i2 && i2 ? (r2 && this.shouldKeepAlternateBranch() ? e4.overwrite(this.alternate.start, this.end, ";") : e4.remove(this.consequent.end, this.end), n2.push(...this.alternateScope.hoistedDeclarations)) : (r2 ? 101 === e4.original.charCodeAt(this.alternate.start - 1) && e4.prependLeft(this.alternate.start, " ") : e4.remove(this.consequent.end, this.alternate.start), this.alternate.render(e4, t2))), this.renderHoistedDeclarations(n2, e4, s2);
  }
  applyDeoptimizations() {
  }
  getTestValue() {
    return this.testValue === cr ? this.testValue = this.test.getLiteralValueAtPath(X, se, this) : this.testValue;
  }
  includeKnownTest(e4, t2) {
    var _a2;
    this.test.shouldBeIncluded(e4) && this.test.include(e4, false), t2 && this.consequent.shouldBeIncluded(e4) && this.consequent.include(e4, false, { asSingleStatement: true }), !t2 && ((_a2 = this.alternate) == null ? void 0 : _a2.shouldBeIncluded(e4)) && this.alternate.include(e4, false, { asSingleStatement: true });
  }
  includeRecursively(e4, t2) {
    var _a2;
    this.test.include(t2, e4), this.consequent.include(t2, e4), (_a2 = this.alternate) == null ? void 0 : _a2.include(t2, e4);
  }
  includeUnknownTest(e4) {
    var _a2;
    this.test.include(e4, false);
    const { brokenFlow: t2 } = e4;
    let s2 = false;
    this.consequent.shouldBeIncluded(e4) && (this.consequent.include(e4, false, { asSingleStatement: true }), s2 = e4.brokenFlow, e4.brokenFlow = t2), ((_a2 = this.alternate) == null ? void 0 : _a2.shouldBeIncluded(e4)) && (this.alternate.include(e4, false, { asSingleStatement: true }), e4.brokenFlow = e4.brokenFlow && s2);
  }
  renderHoistedDeclarations(e4, t2, s2) {
    const i2 = [...new Set(e4.map((e5) => {
      const t3 = e5.variable;
      return t3.included ? t3.getName(s2) : "";
    }))].filter(Boolean).join(", ");
    if (i2) {
      const e5 = this.parent.type, s3 = e5 !== Ts && e5 !== ws;
      t2.prependRight(this.start, `${s3 ? "{ " : ""}var ${i2}; `), s3 && t2.appendLeft(this.end, " }");
    }
  }
  shouldKeepAlternateBranch() {
    let e4 = this.parent;
    do {
      if (e4 instanceof hr && e4.alternate)
        return true;
      if (e4 instanceof kn)
        return false;
      e4 = e4.parent;
    } while (e4);
    return false;
  }
}
class ur extends ei {
  bind() {
  }
  hasEffects() {
    return false;
  }
  initialise() {
    this.context.addImport(this);
  }
  render(e4, t2, s2) {
    e4.remove(s2.start, s2.end);
  }
  applyDeoptimizations() {
  }
}
ur.prototype.needsBoundaries = true;
class dr extends ei {
  applyDeoptimizations() {
  }
}
const pr = "_interopDefault", fr = "_interopDefaultCompat", mr = "_interopNamespace", gr = "_interopNamespaceCompat", yr = "_interopNamespaceDefault", xr = "_interopNamespaceDefaultOnly", Er = "_mergeNamespaces", br = "_documentCurrentScript", vr = { auto: pr, compat: fr, default: null, defaultOnly: null, esModule: null }, Sr = (e4, t2) => "esModule" === e4 || t2 && ("auto" === e4 || "compat" === e4), Ar = { auto: mr, compat: gr, default: yr, defaultOnly: xr, esModule: null }, kr = (e4, t2) => "esModule" !== e4 && Sr(e4, t2), Ir = (e4, t2, s2, i2, n2, r2, o2) => {
  const a2 = new Set(e4);
  for (const e5 of Br)
    t2.has(e5) && a2.add(e5);
  return Br.map((e5) => a2.has(e5) ? wr[e5](s2, i2, n2, r2, o2, a2) : "").join("");
}, wr = { [br]: (e4, { _: t2, n: s2 }) => `var${t2}${br}${t2}=${t2}typeof${t2}document${t2}!==${t2}'undefined'${t2}?${t2}document.currentScript${t2}:${t2}null;${s2}`, [fr](e4, t2, s2) {
  const { _: i2, getDirectReturnFunction: n2, n: r2 } = t2, [o2, a2] = n2(["e"], { functionReturn: true, lineBreakIndent: null, name: fr });
  return `${o2}${$r(t2)}${i2}?${i2}${s2 ? Pr(t2) : Cr(t2)}${a2}${r2}${r2}`;
}, [pr](e4, t2, s2) {
  const { _: i2, getDirectReturnFunction: n2, n: r2 } = t2, [o2, a2] = n2(["e"], { functionReturn: true, lineBreakIndent: null, name: pr });
  return `${o2}e${i2}&&${i2}e.__esModule${i2}?${i2}${s2 ? Pr(t2) : Cr(t2)}${a2}${r2}${r2}`;
}, [gr](e4, t2, s2, i2, n2, r2) {
  const { _: o2, getDirectReturnFunction: a2, n: l2 } = t2;
  if (r2.has(yr)) {
    const [e5, s3] = a2(["e"], { functionReturn: true, lineBreakIndent: null, name: gr });
    return `${e5}${$r(t2)}${o2}?${o2}e${o2}:${o2}${yr}(e)${s3}${l2}${l2}`;
  }
  return `function ${gr}(e)${o2}{${l2}${e4}if${o2}(${$r(t2)})${o2}return e;${l2}` + Nr(e4, e4, t2, s2, i2, n2) + `}${l2}${l2}`;
}, [xr](e4, t2, s2, i2, n2) {
  const { getDirectReturnFunction: r2, getObject: o2, n: a2 } = t2, [l2, c2] = r2(["e"], { functionReturn: true, lineBreakIndent: null, name: xr });
  return `${l2}${Tr(i2, Vr(n2, o2([["__proto__", "null"], ["default", "e"]], { lineBreakIndent: null }), t2))}${c2}${a2}${a2}`;
}, [yr](e4, t2, s2, i2, n2) {
  const { _: r2, n: o2 } = t2;
  return `function ${yr}(e)${r2}{${o2}` + Nr(e4, e4, t2, s2, i2, n2) + `}${o2}${o2}`;
}, [mr](e4, t2, s2, i2, n2, r2) {
  const { _: o2, getDirectReturnFunction: a2, n: l2 } = t2;
  if (r2.has(yr)) {
    const [e5, t3] = a2(["e"], { functionReturn: true, lineBreakIndent: null, name: mr });
    return `${e5}e${o2}&&${o2}e.__esModule${o2}?${o2}e${o2}:${o2}${yr}(e)${t3}${l2}${l2}`;
  }
  return `function ${mr}(e)${o2}{${l2}${e4}if${o2}(e${o2}&&${o2}e.__esModule)${o2}return e;${l2}` + Nr(e4, e4, t2, s2, i2, n2) + `}${l2}${l2}`;
}, [Er](e4, t2, s2, i2, n2) {
  const { _: r2, cnst: o2, n: a2 } = t2, l2 = "var" === o2 && s2;
  return `function ${Er}(n, m)${r2}{${a2}${e4}${Rr(`{${a2}${e4}${e4}${e4}if${r2}(k${r2}!==${r2}'default'${r2}&&${r2}!(k in n))${r2}{${a2}` + (s2 ? l2 ? Or : Dr : Lr)(e4, e4 + e4 + e4 + e4, t2) + `${e4}${e4}${e4}}${a2}${e4}${e4}}`, l2, e4, t2)}${a2}${e4}return ${Tr(i2, Vr(n2, "n", t2))};${a2}}${a2}${a2}`;
} }, Pr = ({ _: e4, getObject: t2 }) => `e${e4}:${e4}${t2([["default", "e"]], { lineBreakIndent: null })}`, Cr = ({ _: e4, getPropertyAccess: t2 }) => `e${t2("default")}${e4}:${e4}e`, $r = ({ _: e4 }) => `e${e4}&&${e4}typeof e${e4}===${e4}'object'${e4}&&${e4}'default'${e4}in e`, Nr = (e4, t2, s2, i2, n2, r2) => {
  const { _: o2, cnst: a2, getObject: l2, getPropertyAccess: c2, n: h2, s: u2 } = s2, d2 = `{${h2}` + (i2 ? Mr : Lr)(e4, t2 + e4 + e4, s2) + `${t2}${e4}}`;
  return `${t2}${a2} n${o2}=${o2}Object.create(null${r2 ? `,${o2}{${o2}[Symbol.toStringTag]:${o2}${zr(l2)}${o2}}` : ""});${h2}${t2}if${o2}(e)${o2}{${h2}${t2}${e4}${_r(d2, !i2, s2)}${h2}${t2}}${h2}${t2}n${c2("default")}${o2}=${o2}e;${h2}${t2}return ${Tr(n2, "n")}${u2}${h2}`;
}, _r = (e4, t2, { _: s2, cnst: i2, getFunctionIntro: n2, s: r2 }) => "var" !== i2 || t2 ? `for${s2}(${i2} k in e)${s2}${e4}` : `Object.keys(e).forEach(${n2(["k"], { isAsync: false, name: null })}${e4})${r2}`, Rr = (e4, t2, s2, { _: i2, cnst: n2, getDirectReturnFunction: r2, getFunctionIntro: o2, n: a2 }) => {
  if (t2) {
    const [t3, n3] = r2(["e"], { functionReturn: false, lineBreakIndent: { base: s2, t: s2 }, name: null });
    return `m.forEach(${t3}e${i2}&&${i2}typeof e${i2}!==${i2}'string'${i2}&&${i2}!Array.isArray(e)${i2}&&${i2}Object.keys(e).forEach(${o2(["k"], { isAsync: false, name: null })}${e4})${n3});`;
  }
  return `for${i2}(var i${i2}=${i2}0;${i2}i${i2}<${i2}m.length;${i2}i++)${i2}{${a2}${s2}${s2}${n2} e${i2}=${i2}m[i];${a2}${s2}${s2}if${i2}(typeof e${i2}!==${i2}'string'${i2}&&${i2}!Array.isArray(e))${i2}{${i2}for${i2}(${n2} k in e)${i2}${e4}${i2}}${a2}${s2}}`;
}, Mr = (e4, t2, s2) => {
  const { _: i2, n: n2 } = s2;
  return `${t2}if${i2}(k${i2}!==${i2}'default')${i2}{${n2}` + Or(e4, t2 + e4, s2) + `${t2}}${n2}`;
}, Or = (e4, t2, { _: s2, cnst: i2, getDirectReturnFunction: n2, n: r2 }) => {
  const [o2, a2] = n2([], { functionReturn: true, lineBreakIndent: null, name: null });
  return `${t2}${i2} d${s2}=${s2}Object.getOwnPropertyDescriptor(e,${s2}k);${r2}${t2}Object.defineProperty(n,${s2}k,${s2}d.get${s2}?${s2}d${s2}:${s2}{${r2}${t2}${e4}enumerable:${s2}true,${r2}${t2}${e4}get:${s2}${o2}e[k]${a2}${r2}${t2}});${r2}`;
}, Dr = (e4, t2, { _: s2, cnst: i2, getDirectReturnFunction: n2, n: r2 }) => {
  const [o2, a2] = n2([], { functionReturn: true, lineBreakIndent: null, name: null });
  return `${t2}${i2} d${s2}=${s2}Object.getOwnPropertyDescriptor(e,${s2}k);${r2}${t2}if${s2}(d)${s2}{${r2}${t2}${e4}Object.defineProperty(n,${s2}k,${s2}d.get${s2}?${s2}d${s2}:${s2}{${r2}${t2}${e4}${e4}enumerable:${s2}true,${r2}${t2}${e4}${e4}get:${s2}${o2}e[k]${a2}${r2}${t2}${e4}});${r2}${t2}}${r2}`;
}, Lr = (e4, t2, { _: s2, n: i2 }) => `${t2}n[k]${s2}=${s2}e[k];${i2}`, Tr = (e4, t2) => e4 ? `Object.freeze(${t2})` : t2, Vr = (e4, t2, { _: s2, getObject: i2 }) => e4 ? `Object.defineProperty(${t2},${s2}Symbol.toStringTag,${s2}${zr(i2)})` : t2, Br = Object.keys(wr);
function zr(e4) {
  return e4([["value", "'Module'"]], { lineBreakIndent: null });
}
function Fr(e4, t2) {
  return null !== e4.renderBaseName && t2.has(e4) && e4.isReassigned;
}
class jr extends ei {
  declareDeclarator(e4) {
    this.id.declare(e4, this.init || rs);
  }
  deoptimizePath(e4) {
    this.id.deoptimizePath(e4);
  }
  hasEffects(e4) {
    var _a2;
    this.deoptimized || this.applyDeoptimizations();
    const t2 = (_a2 = this.init) == null ? void 0 : _a2.hasEffects(e4);
    return this.id.markDeclarationReached(), t2 || this.id.hasEffects(e4);
  }
  include(e4, t2) {
    const { deoptimized: s2, id: i2, init: n2 } = this;
    s2 || this.applyDeoptimizations(), this.included = true, n2 == null ? void 0 : n2.include(e4, t2), i2.markDeclarationReached(), (t2 || i2.shouldBeIncluded(e4)) && i2.include(e4, t2);
  }
  render(e4, t2) {
    const { exportNamesByVariable: s2, snippets: { _: i2, getPropertyAccess: n2 } } = t2, { end: r2, id: o2, init: a2, start: l2 } = this, c2 = o2.included;
    if (c2)
      o2.render(e4, t2);
    else {
      const t3 = mn(e4.original, "=", o2.end);
      e4.remove(l2, yn(e4.original, t3 + 1));
    }
    if (a2) {
      if (o2 instanceof hn && a2 instanceof Jn && !a2.id) {
        o2.variable.getName(n2) !== o2.name && e4.appendLeft(a2.start + 5, ` ${o2.name}`);
      }
      a2.render(e4, t2, c2 ? me : { renderedSurroundingElement: Rs });
    } else
      o2 instanceof hn && Fr(o2.variable, s2) && e4.appendLeft(r2, `${i2}=${i2}void 0`);
  }
  applyDeoptimizations() {
    this.deoptimized = true;
    const { id: e4, init: t2 } = this;
    if (t2 && e4 instanceof hn && t2 instanceof Jn && !t2.id) {
      const { name: s2, variable: i2 } = e4;
      for (const e5 of t2.scope.accessedOutsideVariables.values())
        e5 !== i2 && e5.forbidName(s2);
    }
  }
}
function Ur(e4, t2, s2) {
  return "external" === t2 ? Ar[s2(e4 instanceof Jt ? e4.id : null)] : "default" === t2 ? xr : null;
}
const Gr = { amd: ["require"], cjs: ["require"], system: ["module"] };
function Wr(e4) {
  const t2 = [];
  for (const s2 of e4.properties) {
    if ("RestElement" === s2.type || s2.computed || "Identifier" !== s2.key.type)
      return;
    t2.push(s2.key.name);
  }
  return t2;
}
class qr extends ei {
  applyDeoptimizations() {
  }
}
const Hr = "ROLLUP_FILE_URL_", Kr = "import";
const Yr = ["cjs", "iife", "umd"], Xr = { amd: ["document", "module", "URL"], cjs: ["document", "require", "URL", br], es: [], iife: ["document", "URL", br], system: ["module"], umd: ["document", "require", "URL", br] }, Qr = { amd: ["document", "require", "URL"], cjs: ["document", "require", "URL"], es: [], iife: ["document", "URL"], system: ["module", "URL"], umd: ["document", "require", "URL"] }, Zr = (e4, t2 = "URL") => `new ${t2}(${e4}).href`, Jr = (e4, t2 = false) => Zr(`'${L(e4)}', ${t2 ? "typeof document === 'undefined' ? location.href : " : ""}document.currentScript && document.currentScript.src || document.baseURI`), eo = (e4) => (t2, { chunkId: s2 }) => {
  const i2 = e4(s2);
  return null === t2 ? `({ url: ${i2} })` : "url" === t2 ? i2 : "undefined";
}, to = (e4) => `require('u' + 'rl').pathToFileURL(${e4}).href`, so = (e4) => to(`__dirname + '/${e4}'`), io = (e4, t2 = false) => `${t2 ? "typeof document === 'undefined' ? location.href : " : ""}(${br} && ${br}.src || new URL('${L(e4)}', document.baseURI).href)`, no = { amd: (e4) => ("." !== e4[0] && (e4 = "./" + e4), Zr(`require.toUrl('${e4}'), document.baseURI`)), cjs: (e4) => `(typeof document === 'undefined' ? ${so(e4)} : ${Jr(e4)})`, es: (e4) => Zr(`'${e4}', import.meta.url`), iife: (e4) => Jr(e4), system: (e4) => Zr(`'${e4}', module.meta.url`), umd: (e4) => `(typeof document === 'undefined' && typeof location === 'undefined' ? ${so(e4)} : ${Jr(e4, true)})` }, ro = { amd: eo(() => Zr("module.uri, document.baseURI")), cjs: eo((e4) => `(typeof document === 'undefined' ? ${to("__filename")} : ${io(e4)})`), iife: eo((e4) => io(e4)), system: (e4, { snippets: { getPropertyAccess: t2 } }) => null === e4 ? "module.meta" : `module.meta${t2(e4)}`, umd: eo((e4) => `(typeof document === 'undefined' && typeof location === 'undefined' ? ${to("__filename")} : ${io(e4, true)})`) };
class oo extends ei {
  constructor() {
    super(...arguments), this.hasCachedEffect = null, this.hasLoggedEffect = false;
  }
  hasCachedEffects() {
    return !!this.included && (null === this.hasCachedEffect ? this.hasCachedEffect = this.hasEffects(is()) : this.hasCachedEffect);
  }
  hasEffects(e4) {
    for (const t2 of this.body)
      if (t2.hasEffects(e4)) {
        if (this.context.options.experimentalLogSideEffects && !this.hasLoggedEffect) {
          this.hasLoggedEffect = true;
          const { code: e5, log: s2, module: i2 } = this.context;
          s2(ke, Tt(e5, i2.id, Ce(e5, t2.start, { offsetLine: 1 })), t2.start);
        }
        return this.hasCachedEffect = true;
      }
    return false;
  }
  include(e4, t2) {
    this.included = true;
    for (const s2 of this.body)
      (t2 || s2.shouldBeIncluded(e4)) && s2.include(e4, t2);
  }
  render(e4, t2) {
    let s2 = this.start;
    if (e4.original.startsWith("#!") && (s2 = Math.min(e4.original.indexOf("\n") + 1, this.end), e4.remove(0, s2)), this.body.length > 0) {
      for (; "/" === e4.original[s2] && /[*/]/.test(e4.original[s2 + 1]); ) {
        const t3 = xn(e4.original.slice(s2, this.body[0].start));
        if (-1 === t3[0])
          break;
        s2 += t3[1];
      }
      En(this.body, e4, s2, this.end, t2);
    } else
      super.render(e4, t2);
  }
  applyDeoptimizations() {
  }
}
class ao extends ei {
  hasEffects(e4) {
    var _a2;
    if ((_a2 = this.test) == null ? void 0 : _a2.hasEffects(e4))
      return true;
    for (const t2 of this.consequent) {
      if (e4.brokenFlow)
        break;
      if (t2.hasEffects(e4))
        return true;
    }
    return false;
  }
  include(e4, t2) {
    var _a2;
    this.included = true, (_a2 = this.test) == null ? void 0 : _a2.include(e4, t2);
    for (const s2 of this.consequent)
      (t2 || s2.shouldBeIncluded(e4)) && s2.include(e4, t2);
  }
  render(e4, t2, s2) {
    if (this.consequent.length > 0) {
      this.test && this.test.render(e4, t2);
      const i2 = this.test ? this.test.end : mn(e4.original, "default", this.start) + 7, n2 = mn(e4.original, ":", i2) + 1;
      En(this.consequent, e4, n2, s2.end, t2);
    } else
      super.render(e4, t2);
  }
}
ao.prototype.needsBoundaries = true;
class lo extends ei {
  deoptimizeArgumentsOnInteractionAtPath() {
  }
  getLiteralValueAtPath(e4) {
    return e4.length > 0 || 1 !== this.quasis.length ? ne : this.quasis[0].value.cooked;
  }
  getReturnExpressionWhenCalledAtPath(e4) {
    return 1 !== e4.length ? le : bs(xs, e4[0]);
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    return 0 === t2.type ? e4.length > 1 : 2 !== t2.type || 1 !== e4.length || Es(xs, e4[0], t2, s2);
  }
  render(e4, t2) {
    e4.indentExclusionRanges.push([this.start, this.end]), super.render(e4, t2);
  }
}
class co extends pe {
  constructor() {
    super("undefined");
  }
  getLiteralValueAtPath() {
  }
}
class ho extends Pi {
  constructor(e4, t2, s2) {
    super(e4, t2, t2.declaration, s2), this.hasId = false, this.originalId = null, this.originalVariable = null;
    const i2 = t2.declaration;
    (i2 instanceof nr || i2 instanceof Zn) && i2.id ? (this.hasId = true, this.originalId = i2.id) : i2 instanceof hn && (this.originalId = i2);
  }
  addReference(e4) {
    this.hasId || (this.name = e4.name);
  }
  forbidName(e4) {
    const t2 = this.getOriginalVariable();
    t2 === this ? super.forbidName(e4) : t2.forbidName(e4);
  }
  getAssignedVariableName() {
    return this.originalId && this.originalId.name || null;
  }
  getBaseVariableName() {
    const e4 = this.getOriginalVariable();
    return e4 === this ? super.getBaseVariableName() : e4.getBaseVariableName();
  }
  getDirectOriginalVariable() {
    return !this.originalId || !this.hasId && (this.originalId.isPossibleTDZ() || this.originalId.variable.isReassigned || this.originalId.variable instanceof co || "syntheticNamespace" in this.originalId.variable) ? null : this.originalId.variable;
  }
  getName(e4) {
    const t2 = this.getOriginalVariable();
    return t2 === this ? super.getName(e4) : t2.getName(e4);
  }
  getOriginalVariable() {
    if (this.originalVariable)
      return this.originalVariable;
    let e4, t2 = this;
    const s2 = /* @__PURE__ */ new Set();
    do {
      s2.add(t2), e4 = t2, t2 = e4.getDirectOriginalVariable();
    } while (t2 instanceof ho && !s2.has(t2));
    return this.originalVariable = t2 || e4;
  }
}
class uo extends Vi {
  constructor(e4, t2) {
    super(e4), this.context = t2, this.variables.set("this", new Pi("this", null, rs, t2));
  }
  addExportDefaultDeclaration(e4, t2, s2) {
    const i2 = new ho(e4, t2, s2);
    return this.variables.set("default", i2), i2;
  }
  addNamespaceMemberAccess() {
  }
  deconflict(e4, t2, s2) {
    for (const i2 of this.children)
      i2.deconflict(e4, t2, s2);
  }
  findLexicalBoundary() {
    return this;
  }
  findVariable(e4) {
    const t2 = this.variables.get(e4) || this.accessedOutsideVariables.get(e4);
    if (t2)
      return t2;
    const s2 = this.context.traceVariable(e4) || this.parent.findVariable(e4);
    return s2 instanceof ln && this.accessedOutsideVariables.set(e4, s2), s2;
  }
}
const po = { "!": (e4) => !e4, "+": (e4) => +e4, "-": (e4) => -e4, delete: () => ne, typeof: (e4) => typeof e4, void: () => {
}, "~": (e4) => ~e4 };
class fo extends ei {
  deoptimizePath() {
    for (const e4 of this.declarations)
      e4.deoptimizePath(X);
  }
  hasEffectsOnInteractionAtPath() {
    return false;
  }
  include(e4, t2, { asSingleStatement: s2 } = me) {
    this.included = true;
    for (const i2 of this.declarations) {
      (t2 || i2.shouldBeIncluded(e4)) && i2.include(e4, t2);
      const { id: n2, init: r2 } = i2;
      s2 && n2.include(e4, t2), r2 && n2.included && !r2.included && (n2 instanceof _n || n2 instanceof wi) && r2.include(e4, t2);
    }
  }
  initialise() {
    for (const e4 of this.declarations)
      e4.declareDeclarator(this.kind);
  }
  render(e4, t2, s2 = me) {
    if (function(e5, t3) {
      for (const s3 of e5) {
        if (!s3.id.included)
          return false;
        if (s3.id.type === Os) {
          if (t3.has(s3.id.variable))
            return false;
        } else {
          const e6 = [];
          if (s3.id.addExportedVariables(e6, t3), e6.length > 0)
            return false;
        }
      }
      return true;
    }(this.declarations, t2.exportNamesByVariable)) {
      for (const s3 of this.declarations)
        s3.render(e4, t2);
      s2.isNoStatement || 59 === e4.original.charCodeAt(this.end - 1) || e4.appendLeft(this.end, ";");
    } else
      this.renderReplacedDeclarations(e4, t2);
  }
  applyDeoptimizations() {
  }
  renderDeclarationEnd(e4, t2, s2, i2, n2, r2, o2) {
    59 === e4.original.charCodeAt(this.end - 1) && e4.remove(this.end - 1, this.end), t2 += ";", null === s2 ? e4.appendLeft(n2, t2) : (10 !== e4.original.charCodeAt(i2 - 1) || 10 !== e4.original.charCodeAt(this.end) && 13 !== e4.original.charCodeAt(this.end) || (i2--, 13 === e4.original.charCodeAt(i2) && i2--), i2 === s2 + 1 ? e4.overwrite(s2, n2, t2) : (e4.overwrite(s2, s2 + 1, t2), e4.remove(i2, n2))), r2.length > 0 && e4.appendLeft(n2, ` ${Cn(r2, o2)};`);
  }
  renderReplacedDeclarations(e4, t2) {
    const s2 = bn(this.declarations, e4, this.start + this.kind.length, this.end - (59 === e4.original.charCodeAt(this.end - 1) ? 1 : 0));
    let i2, n2;
    n2 = yn(e4.original, this.start + this.kind.length);
    let r2 = n2 - 1;
    e4.remove(this.start, r2);
    let o2, l2 = false, c2 = false, h2 = "";
    const u2 = [], d2 = function(e5, t3, s3) {
      var _a2;
      let i3 = null;
      if ("system" === t3.format) {
        for (const { node: n3 } of e5)
          n3.id instanceof hn && n3.init && 0 === s3.length && 1 === ((_a2 = t3.exportNamesByVariable.get(n3.id.variable)) == null ? void 0 : _a2.length) ? (i3 = n3.id.variable, s3.push(i3)) : n3.id.addExportedVariables(s3, t3.exportNamesByVariable);
        s3.length > 1 ? i3 = null : i3 && (s3.length = 0);
      }
      return i3;
    }(s2, t2, u2);
    for (const { node: u3, start: p2, separator: f2, contentEnd: m2, end: g2 } of s2)
      if (u3.included) {
        if (u3.render(e4, t2), o2 = "", !u3.id.included || u3.id instanceof hn && Fr(u3.id.variable, t2.exportNamesByVariable))
          c2 && (h2 += ";"), l2 = false;
        else {
          if (d2 && d2 === u3.id.variable) {
            const s3 = mn(e4.original, "=", u3.id.end);
            $n(d2, yn(e4.original, s3 + 1), null === f2 ? m2 : f2, e4, t2);
          }
          l2 ? h2 += "," : (c2 && (h2 += ";"), o2 += `${this.kind} `, l2 = true);
        }
        n2 === r2 + 1 ? e4.overwrite(r2, n2, h2 + o2) : (e4.overwrite(r2, r2 + 1, h2), e4.appendLeft(n2, o2)), i2 = m2, n2 = g2, c2 = true, r2 = f2, h2 = "";
      } else
        e4.remove(p2, g2);
    this.renderDeclarationEnd(e4, h2, r2, i2, n2, u2, t2);
  }
}
const mo = { ArrayExpression: Ii, ArrayPattern: wi, ArrowFunctionExpression: Pn, AssignmentExpression: class extends ei {
  hasEffects(e4) {
    const { deoptimized: t2, left: s2, operator: i2, right: n2 } = this;
    return t2 || this.applyDeoptimizations(), n2.hasEffects(e4) || s2.hasEffectsAsAssignmentTarget(e4, "=" !== i2);
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    return this.right.hasEffectsOnInteractionAtPath(e4, t2, s2);
  }
  include(e4, t2) {
    const { deoptimized: s2, left: i2, right: n2, operator: r2 } = this;
    s2 || this.applyDeoptimizations(), this.included = true, (t2 || "=" !== r2 || i2.included || i2.hasEffectsAsAssignmentTarget(is(), false)) && i2.includeAsAssignmentTarget(e4, t2, "=" !== r2), n2.include(e4, t2);
  }
  initialise() {
    this.left.setAssignedValue(this.right);
  }
  render(e4, t2, { preventASI: s2, renderedParentType: i2, renderedSurroundingElement: n2 } = me) {
    const { left: r2, right: o2, start: a2, end: l2, parent: c2 } = this;
    if (r2.included)
      r2.render(e4, t2), o2.render(e4, t2);
    else {
      const l3 = yn(e4.original, mn(e4.original, "=", r2.end) + 1);
      e4.remove(a2, l3), s2 && vn(e4, l3, o2.start), o2.render(e4, t2, { renderedParentType: i2 || c2.type, renderedSurroundingElement: n2 || c2.type });
    }
    if ("system" === t2.format)
      if (r2 instanceof hn) {
        const s3 = r2.variable, i3 = t2.exportNamesByVariable.get(s3);
        if (i3)
          return void (1 === i3.length ? $n(s3, a2, l2, e4, t2) : Nn(s3, a2, l2, c2.type !== Rs, e4, t2));
      } else {
        const s3 = [];
        if (r2.addExportedVariables(s3, t2.exportNamesByVariable), s3.length > 0)
          return void function(e5, t3, s4, i3, n3, r3) {
            const { _: o3, getDirectReturnIifeLeft: a3 } = r3.snippets;
            n3.prependRight(t3, a3(["v"], `${Cn(e5, r3)},${o3}v`, { needsArrowReturnParens: true, needsWrappedFunction: i3 })), n3.appendLeft(s4, ")");
          }(s3, a2, l2, n2 === Rs, e4, t2);
      }
    r2.included && r2 instanceof _n && (n2 === Rs || n2 === ks) && (e4.appendRight(a2, "("), e4.prependLeft(l2, ")"));
  }
  applyDeoptimizations() {
    this.deoptimized = true, this.left.deoptimizePath(X), this.right.deoptimizePath(Q), this.context.requestTreeshakingPass();
  }
}, AssignmentPattern: class extends ei {
  addExportedVariables(e4, t2) {
    this.left.addExportedVariables(e4, t2);
  }
  declare(e4, t2) {
    return this.left.declare(e4, t2);
  }
  deoptimizePath(e4) {
    0 === e4.length && this.left.deoptimizePath(e4);
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    return e4.length > 0 || this.left.hasEffectsOnInteractionAtPath(X, t2, s2);
  }
  markDeclarationReached() {
    this.left.markDeclarationReached();
  }
  render(e4, t2, { isShorthandProperty: s2 } = me) {
    this.left.render(e4, t2, { isShorthandProperty: s2 }), this.right.render(e4, t2);
  }
  applyDeoptimizations() {
    this.deoptimized = true, this.left.deoptimizePath(X), this.right.deoptimizePath(Q), this.context.requestTreeshakingPass();
  }
}, AwaitExpression: Ln, BinaryExpression: class extends ei {
  deoptimizeCache() {
  }
  getLiteralValueAtPath(e4, t2, s2) {
    if (e4.length > 0)
      return ne;
    const i2 = this.left.getLiteralValueAtPath(X, t2, s2);
    if ("symbol" == typeof i2)
      return ne;
    const n2 = this.right.getLiteralValueAtPath(X, t2, s2);
    if ("symbol" == typeof n2)
      return ne;
    const r2 = Tn[this.operator];
    return r2 ? r2(i2, n2) : ne;
  }
  hasEffects(e4) {
    return "+" === this.operator && this.parent instanceof An && "" === this.left.getLiteralValueAtPath(X, se, this) || super.hasEffects(e4);
  }
  hasEffectsOnInteractionAtPath(e4, { type: t2 }) {
    return 0 !== t2 || e4.length > 1;
  }
  render(e4, t2, { renderedSurroundingElement: s2 } = me) {
    this.left.render(e4, t2, { renderedSurroundingElement: s2 }), this.right.render(e4, t2);
  }
}, BlockStatement: kn, BreakStatement: class extends ei {
  hasEffects(e4) {
    if (this.label) {
      if (!e4.ignore.labels.has(this.label.name))
        return true;
      e4.includedLabels.add(this.label.name);
    } else {
      if (!e4.ignore.breaks)
        return true;
      e4.hasBreak = true;
    }
    return e4.brokenFlow = true, false;
  }
  include(e4) {
    this.included = true, this.label ? (this.label.include(), e4.includedLabels.add(this.label.name)) : e4.hasBreak = true, e4.brokenFlow = true;
  }
}, CallExpression: Wn, CatchClause: class extends ei {
  createScope(e4) {
    this.scope = new qn(e4, this.context);
  }
  parseNode(e4) {
    const { param: t2 } = e4;
    t2 && (this.param = new (this.context.getNodeConstructor(t2.type))(t2, this, this.scope), this.param.declare("parameter", ae)), super.parseNode(e4);
  }
}, ChainExpression: class extends ei {
  deoptimizeCache() {
  }
  getLiteralValueAtPath(e4, t2, s2) {
    if (!this.expression.isSkippedAsOptional(s2))
      return this.expression.getLiteralValueAtPath(e4, t2, s2);
  }
  hasEffects(e4) {
    return !this.expression.isSkippedAsOptional(this) && this.expression.hasEffects(e4);
  }
}, ClassBody: class extends ei {
  createScope(e4) {
    this.scope = new Hn(e4, this.parent, this.context);
  }
  include(e4, t2) {
    this.included = true, this.context.includeVariableInModule(this.scope.thisVariable);
    for (const s2 of this.body)
      s2.include(e4, t2);
  }
  parseNode(e4) {
    const t2 = this.body = [];
    for (const s2 of e4.body)
      t2.push(new (this.context.getNodeConstructor(s2.type))(s2, this, s2.static ? this.scope : this.scope.instanceScope));
    super.parseNode(e4);
  }
  applyDeoptimizations() {
  }
}, ClassDeclaration: Zn, ClassExpression: Jn, ConditionalExpression: class extends ei {
  constructor() {
    super(...arguments), this.expressionsToBeDeoptimized = [], this.isBranchResolutionAnalysed = false, this.usedBranch = null;
  }
  deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2) {
    this.consequent.deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2), this.alternate.deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2);
  }
  deoptimizeCache() {
    if (null !== this.usedBranch) {
      const e4 = this.usedBranch === this.consequent ? this.alternate : this.consequent;
      this.usedBranch = null, e4.deoptimizePath(Q);
      const { expressionsToBeDeoptimized: t2 } = this;
      this.expressionsToBeDeoptimized = ye;
      for (const e5 of t2)
        e5.deoptimizeCache();
    }
  }
  deoptimizePath(e4) {
    const t2 = this.getUsedBranch();
    t2 ? t2.deoptimizePath(e4) : (this.consequent.deoptimizePath(e4), this.alternate.deoptimizePath(e4));
  }
  getLiteralValueAtPath(e4, t2, s2) {
    const i2 = this.getUsedBranch();
    return i2 ? (this.expressionsToBeDeoptimized.push(s2), i2.getLiteralValueAtPath(e4, t2, s2)) : ne;
  }
  getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2) {
    const n2 = this.getUsedBranch();
    return n2 ? (this.expressionsToBeDeoptimized.push(i2), n2.getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2)) : [new er([this.consequent.getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2)[0], this.alternate.getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2)[0]]), false];
  }
  hasEffects(e4) {
    if (this.test.hasEffects(e4))
      return true;
    const t2 = this.getUsedBranch();
    return t2 ? t2.hasEffects(e4) : this.consequent.hasEffects(e4) || this.alternate.hasEffects(e4);
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    const i2 = this.getUsedBranch();
    return i2 ? i2.hasEffectsOnInteractionAtPath(e4, t2, s2) : this.consequent.hasEffectsOnInteractionAtPath(e4, t2, s2) || this.alternate.hasEffectsOnInteractionAtPath(e4, t2, s2);
  }
  include(e4, t2) {
    this.included = true;
    const s2 = this.getUsedBranch();
    t2 || this.test.shouldBeIncluded(e4) || null === s2 ? (this.test.include(e4, t2), this.consequent.include(e4, t2), this.alternate.include(e4, t2)) : s2.include(e4, t2);
  }
  includeCallArguments(e4, t2) {
    const s2 = this.getUsedBranch();
    s2 ? s2.includeCallArguments(e4, t2) : (this.consequent.includeCallArguments(e4, t2), this.alternate.includeCallArguments(e4, t2));
  }
  render(e4, t2, { isCalleeOfRenderedParent: s2, preventASI: i2, renderedParentType: n2, renderedSurroundingElement: r2 } = me) {
    const o2 = this.getUsedBranch();
    if (this.test.included)
      this.test.render(e4, t2, { renderedSurroundingElement: r2 }), this.consequent.render(e4, t2), this.alternate.render(e4, t2);
    else {
      const a2 = mn(e4.original, ":", this.consequent.end), l2 = yn(e4.original, (this.consequent.included ? mn(e4.original, "?", this.test.end) : a2) + 1);
      i2 && vn(e4, l2, o2.start), e4.remove(this.start, l2), this.consequent.included && e4.remove(a2, this.end), pn(this, e4), o2.render(e4, t2, { isCalleeOfRenderedParent: s2, preventASI: true, renderedParentType: n2 || this.parent.type, renderedSurroundingElement: r2 || this.parent.type });
    }
  }
  getUsedBranch() {
    if (this.isBranchResolutionAnalysed)
      return this.usedBranch;
    this.isBranchResolutionAnalysed = true;
    const e4 = this.test.getLiteralValueAtPath(X, se, this);
    return "symbol" == typeof e4 ? null : this.usedBranch = e4 ? this.consequent : this.alternate;
  }
}, ContinueStatement: class extends ei {
  hasEffects(e4) {
    if (this.label) {
      if (!e4.ignore.labels.has(this.label.name))
        return true;
      e4.includedLabels.add(this.label.name);
    } else {
      if (!e4.ignore.continues)
        return true;
      e4.hasContinue = true;
    }
    return e4.brokenFlow = true, false;
  }
  include(e4) {
    this.included = true, this.label ? (this.label.include(), e4.includedLabels.add(this.label.name)) : e4.hasContinue = true, e4.brokenFlow = true;
  }
}, DoWhileStatement: class extends ei {
  hasEffects(e4) {
    return !!this.test.hasEffects(e4) || tr(e4, this.body);
  }
  include(e4, t2) {
    this.included = true, this.test.include(e4, t2), sr(e4, this.body, t2);
  }
}, EmptyStatement: class extends ei {
  hasEffects() {
    return false;
  }
}, ExportAllDeclaration: ir, ExportDefaultDeclaration: rr, ExportNamedDeclaration: or, ExportSpecifier: class extends ei {
  applyDeoptimizations() {
  }
}, ExpressionStatement: An, ForInStatement: class extends ei {
  createScope(e4) {
    this.scope = new Sn(e4);
  }
  hasEffects(e4) {
    const { body: t2, deoptimized: s2, left: i2, right: n2 } = this;
    return s2 || this.applyDeoptimizations(), !(!i2.hasEffectsAsAssignmentTarget(e4, false) && !n2.hasEffects(e4)) || tr(e4, t2);
  }
  include(e4, t2) {
    const { body: s2, deoptimized: i2, left: n2, right: r2 } = this;
    i2 || this.applyDeoptimizations(), this.included = true, n2.includeAsAssignmentTarget(e4, t2 || true, false), r2.include(e4, t2), sr(e4, s2, t2);
  }
  initialise() {
    this.left.setAssignedValue(ae);
  }
  render(e4, t2) {
    this.left.render(e4, t2, fn), this.right.render(e4, t2, fn), 110 === e4.original.charCodeAt(this.right.start - 1) && e4.prependLeft(this.right.start, " "), this.body.render(e4, t2);
  }
  applyDeoptimizations() {
    this.deoptimized = true, this.left.deoptimizePath(X), this.context.requestTreeshakingPass();
  }
}, ForOfStatement: class extends ei {
  createScope(e4) {
    this.scope = new Sn(e4);
  }
  hasEffects() {
    return this.deoptimized || this.applyDeoptimizations(), true;
  }
  include(e4, t2) {
    const { body: s2, deoptimized: i2, left: n2, right: r2 } = this;
    i2 || this.applyDeoptimizations(), this.included = true, n2.includeAsAssignmentTarget(e4, t2 || true, false), r2.include(e4, t2), sr(e4, s2, t2);
  }
  initialise() {
    this.left.setAssignedValue(ae);
  }
  render(e4, t2) {
    this.left.render(e4, t2, fn), this.right.render(e4, t2, fn), 102 === e4.original.charCodeAt(this.right.start - 1) && e4.prependLeft(this.right.start, " "), this.body.render(e4, t2);
  }
  applyDeoptimizations() {
    this.deoptimized = true, this.left.deoptimizePath(X), this.right.deoptimizePath(Q), this.context.requestTreeshakingPass();
  }
}, ForStatement: class extends ei {
  createScope(e4) {
    this.scope = new Sn(e4);
  }
  hasEffects(e4) {
    var _a2, _b, _c2;
    return !!(((_a2 = this.init) == null ? void 0 : _a2.hasEffects(e4)) || ((_b = this.test) == null ? void 0 : _b.hasEffects(e4)) || ((_c2 = this.update) == null ? void 0 : _c2.hasEffects(e4))) || tr(e4, this.body);
  }
  include(e4, t2) {
    var _a2, _b, _c2;
    this.included = true, (_a2 = this.init) == null ? void 0 : _a2.include(e4, t2, { asSingleStatement: true }), (_b = this.test) == null ? void 0 : _b.include(e4, t2), (_c2 = this.update) == null ? void 0 : _c2.include(e4, t2), sr(e4, this.body, t2);
  }
  render(e4, t2) {
    var _a2, _b, _c2;
    (_a2 = this.init) == null ? void 0 : _a2.render(e4, t2, fn), (_b = this.test) == null ? void 0 : _b.render(e4, t2, fn), (_c2 = this.update) == null ? void 0 : _c2.render(e4, t2, fn), this.body.render(e4, t2);
  }
}, FunctionDeclaration: nr, FunctionExpression: ar, Identifier: hn, IfStatement: hr, ImportAttribute: class extends ei {
}, ImportDeclaration: ur, ImportDefaultSpecifier: dr, ImportExpression: class extends ei {
  constructor() {
    super(...arguments), this.inlineNamespace = null, this.assertions = null, this.mechanism = null, this.namespaceExportName = void 0, this.resolution = null, this.resolutionString = null;
  }
  bind() {
    this.source.bind();
  }
  getDeterministicImportedNames() {
    const e4 = this.parent;
    if (e4 instanceof An)
      return ye;
    if (e4 instanceof Ln) {
      const t2 = e4.parent;
      if (t2 instanceof An)
        return ye;
      if (t2 instanceof jr) {
        const e5 = t2.id;
        return e5 instanceof _n ? Wr(e5) : void 0;
      }
      if (t2 instanceof jn) {
        const e5 = t2.property;
        if (!t2.computed && e5 instanceof hn)
          return [e5.name];
      }
    } else if (e4 instanceof jn) {
      const t2 = e4.parent, s2 = e4.property;
      if (!(t2 instanceof Wn && s2 instanceof hn))
        return;
      const i2 = s2.name;
      if (t2.parent instanceof An && ["catch", "finally"].includes(i2))
        return ye;
      if ("then" !== i2)
        return;
      if (0 === t2.arguments.length)
        return ye;
      const n2 = t2.arguments[0];
      if (1 !== t2.arguments.length || !(n2 instanceof Pn || n2 instanceof ar))
        return;
      if (0 === n2.params.length)
        return ye;
      const r2 = n2.params[0];
      return 1 === n2.params.length && r2 instanceof _n ? Wr(r2) : void 0;
    }
  }
  hasEffects() {
    return true;
  }
  include(e4, t2) {
    this.included || (this.included = true, this.context.includeDynamicImport(this), this.scope.addAccessedDynamicImport(this)), this.source.include(e4, t2);
  }
  initialise() {
    this.context.addDynamicImport(this);
  }
  parseNode(e4) {
    super.parseNode(e4, ["source"]);
  }
  render(e4, t2) {
    const { snippets: { _: s2, getDirectReturnFunction: i2, getObject: n2, getPropertyAccess: r2 } } = t2;
    if (this.inlineNamespace) {
      const [t3, s3] = i2([], { functionReturn: true, lineBreakIndent: null, name: null });
      e4.overwrite(this.start, this.end, `Promise.resolve().then(${t3}${this.inlineNamespace.getName(r2)}${s3})`);
    } else {
      if (this.mechanism && (e4.overwrite(this.start, mn(e4.original, "(", this.start + 6) + 1, this.mechanism.left), e4.overwrite(this.end - 1, this.end, this.mechanism.right)), this.resolutionString) {
        if (e4.overwrite(this.source.start, this.source.end, this.resolutionString), this.namespaceExportName) {
          const [t3, s3] = i2(["n"], { functionReturn: true, lineBreakIndent: null, name: null });
          e4.prependLeft(this.end, `.then(${t3}n.${this.namespaceExportName}${s3})`);
        }
      } else
        this.source.render(e4, t2);
      true !== this.assertions && (this.arguments && e4.overwrite(this.source.end, this.end - 1, "", { contentOnly: true }), this.assertions && e4.appendLeft(this.end - 1, `,${s2}${n2([["assert", this.assertions]], { lineBreakIndent: null })}`));
    }
  }
  setExternalResolution(e4, t2, s2, i2, n2, r2, o2, a2, l2) {
    const { format: c2 } = s2;
    this.inlineNamespace = null, this.resolution = t2, this.resolutionString = o2, this.namespaceExportName = a2, this.assertions = l2;
    const h2 = [...Gr[c2] || []];
    let u2;
    ({ helper: u2, mechanism: this.mechanism } = this.getDynamicImportMechanismAndHelper(t2, e4, s2, i2, n2)), u2 && h2.push(u2), h2.length > 0 && this.scope.addAccessedGlobals(h2, r2);
  }
  setInternalResolution(e4) {
    this.inlineNamespace = e4;
  }
  applyDeoptimizations() {
  }
  getDynamicImportMechanismAndHelper(e4, t2, { compact: s2, dynamicImportFunction: i2, dynamicImportInCjs: n2, format: r2, generatedCode: { arrowFunctions: o2 }, interop: a2 }, { _: l2, getDirectReturnFunction: c2, getDirectReturnIifeLeft: h2 }, u2) {
    const d2 = u2.hookFirstSync("renderDynamicImport", [{ customResolution: "string" == typeof this.resolution ? this.resolution : null, format: r2, moduleId: this.context.module.id, targetModuleId: this.resolution && "string" != typeof this.resolution ? this.resolution.id : null }]);
    if (d2)
      return { helper: null, mechanism: d2 };
    const p2 = !this.resolution || "string" == typeof this.resolution;
    switch (r2) {
      case "cjs": {
        if (n2 && (!e4 || "string" == typeof e4 || e4 instanceof Jt))
          return { helper: null, mechanism: null };
        const s3 = Ur(e4, t2, a2);
        let i3 = "require(", r3 = ")";
        s3 && (i3 = `/*#__PURE__*/${s3}(${i3}`, r3 += ")");
        const [l3, u3] = c2([], { functionReturn: true, lineBreakIndent: null, name: null });
        return i3 = `Promise.resolve().then(${l3}${i3}`, r3 += `${u3})`, !o2 && p2 && (i3 = h2(["t"], `${i3}t${r3}`, { needsArrowReturnParens: false, needsWrappedFunction: true }), r3 = ")"), { helper: s3, mechanism: { left: i3, right: r3 } };
      }
      case "amd": {
        const i3 = s2 ? "c" : "resolve", n3 = s2 ? "e" : "reject", r3 = Ur(e4, t2, a2), [u3, d3] = c2(["m"], { functionReturn: false, lineBreakIndent: null, name: null }), f2 = r3 ? `${u3}${i3}(/*#__PURE__*/${r3}(m))${d3}` : i3, [m2, g2] = c2([i3, n3], { functionReturn: false, lineBreakIndent: null, name: null });
        let y2 = `new Promise(${m2}require([`, x2 = `],${l2}${f2},${l2}${n3})${g2})`;
        return !o2 && p2 && (y2 = h2(["t"], `${y2}t${x2}`, { needsArrowReturnParens: false, needsWrappedFunction: true }), x2 = ")"), { helper: r3, mechanism: { left: y2, right: x2 } };
      }
      case "system":
        return { helper: null, mechanism: { left: "module.import(", right: ")" } };
      case "es":
        if (i2)
          return { helper: null, mechanism: { left: `${i2}(`, right: ")" } };
    }
    return { helper: null, mechanism: null };
  }
}, ImportNamespaceSpecifier: qr, ImportSpecifier: class extends ei {
  applyDeoptimizations() {
  }
}, LabeledStatement: class extends ei {
  hasEffects(e4) {
    const t2 = e4.brokenFlow;
    return e4.ignore.labels.add(this.label.name), !!this.body.hasEffects(e4) || (e4.ignore.labels.delete(this.label.name), e4.includedLabels.has(this.label.name) && (e4.includedLabels.delete(this.label.name), e4.brokenFlow = t2), false);
  }
  include(e4, t2) {
    this.included = true;
    const s2 = e4.brokenFlow;
    this.body.include(e4, t2), (t2 || e4.includedLabels.has(this.label.name)) && (this.label.include(), e4.includedLabels.delete(this.label.name), e4.brokenFlow = s2);
  }
  render(e4, t2) {
    this.label.included ? this.label.render(e4, t2) : e4.remove(this.start, yn(e4.original, mn(e4.original, ":", this.label.end) + 1)), this.body.render(e4, t2);
  }
}, Literal: Bn, LogicalExpression: class extends ei {
  constructor() {
    super(...arguments), this.expressionsToBeDeoptimized = [], this.isBranchResolutionAnalysed = false, this.usedBranch = null;
  }
  deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2) {
    this.left.deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2), this.right.deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2);
  }
  deoptimizeCache() {
    if (this.usedBranch) {
      const e4 = this.usedBranch === this.left ? this.right : this.left;
      this.usedBranch = null, e4.deoptimizePath(Q);
      const { context: t2, expressionsToBeDeoptimized: s2 } = this;
      this.expressionsToBeDeoptimized = ye;
      for (const e5 of s2)
        e5.deoptimizeCache();
      t2.requestTreeshakingPass();
    }
  }
  deoptimizePath(e4) {
    const t2 = this.getUsedBranch();
    t2 ? t2.deoptimizePath(e4) : (this.left.deoptimizePath(e4), this.right.deoptimizePath(e4));
  }
  getLiteralValueAtPath(e4, t2, s2) {
    const i2 = this.getUsedBranch();
    return i2 ? (this.expressionsToBeDeoptimized.push(s2), i2.getLiteralValueAtPath(e4, t2, s2)) : ne;
  }
  getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2) {
    const n2 = this.getUsedBranch();
    return n2 ? (this.expressionsToBeDeoptimized.push(i2), n2.getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2)) : [new er([this.left.getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2)[0], this.right.getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2)[0]]), false];
  }
  hasEffects(e4) {
    return !!this.left.hasEffects(e4) || this.getUsedBranch() !== this.left && this.right.hasEffects(e4);
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    const i2 = this.getUsedBranch();
    return i2 ? i2.hasEffectsOnInteractionAtPath(e4, t2, s2) : this.left.hasEffectsOnInteractionAtPath(e4, t2, s2) || this.right.hasEffectsOnInteractionAtPath(e4, t2, s2);
  }
  include(e4, t2) {
    this.included = true;
    const s2 = this.getUsedBranch();
    t2 || s2 === this.right && this.left.shouldBeIncluded(e4) || !s2 ? (this.left.include(e4, t2), this.right.include(e4, t2)) : s2.include(e4, t2);
  }
  render(e4, t2, { isCalleeOfRenderedParent: s2, preventASI: i2, renderedParentType: n2, renderedSurroundingElement: r2 } = me) {
    if (this.left.included && this.right.included)
      this.left.render(e4, t2, { preventASI: i2, renderedSurroundingElement: r2 }), this.right.render(e4, t2);
    else {
      const o2 = mn(e4.original, this.operator, this.left.end);
      if (this.right.included) {
        const t3 = yn(e4.original, o2 + 2);
        e4.remove(this.start, t3), i2 && vn(e4, t3, this.right.start);
      } else
        e4.remove(o2, this.end);
      pn(this, e4), this.getUsedBranch().render(e4, t2, { isCalleeOfRenderedParent: s2, preventASI: i2, renderedParentType: n2 || this.parent.type, renderedSurroundingElement: r2 || this.parent.type });
    }
  }
  getUsedBranch() {
    if (!this.isBranchResolutionAnalysed) {
      this.isBranchResolutionAnalysed = true;
      const e4 = this.left.getLiteralValueAtPath(X, se, this);
      if ("symbol" == typeof e4)
        return null;
      this.usedBranch = "||" === this.operator && e4 || "&&" === this.operator && !e4 || "??" === this.operator && null != e4 ? this.left : this.right;
    }
    return this.usedBranch;
  }
}, MemberExpression: jn, MetaProperty: class extends ei {
  constructor() {
    super(...arguments), this.metaProperty = null, this.preliminaryChunkId = null, this.referenceId = null;
  }
  getReferencedFileName(e4) {
    const { meta: { name: t2 }, metaProperty: s2 } = this;
    return t2 === Kr && (s2 == null ? void 0 : s2.startsWith(Hr)) ? e4.getFileName(s2.slice(16)) : null;
  }
  hasEffects() {
    return false;
  }
  hasEffectsOnInteractionAtPath(e4, { type: t2 }) {
    return e4.length > 1 || 0 !== t2;
  }
  include() {
    if (!this.included && (this.included = true, this.meta.name === Kr)) {
      this.context.addImportMeta(this);
      const e4 = this.parent, t2 = this.metaProperty = e4 instanceof jn && "string" == typeof e4.propertyKey ? e4.propertyKey : null;
      (t2 == null ? void 0 : t2.startsWith(Hr)) && (this.referenceId = t2.slice(16));
    }
  }
  render(e4, t2) {
    var _a2;
    const { format: s2, pluginDriver: i2, snippets: n2 } = t2, { context: { module: r2 }, meta: { name: o2 }, metaProperty: a2, parent: l2, preliminaryChunkId: c2, referenceId: h2, start: u2, end: d2 } = this, { id: p2 } = r2;
    if (o2 !== Kr)
      return;
    const f2 = c2;
    if (h2) {
      const t3 = i2.getFileName(h2), n3 = P(_($(f2), t3)), r3 = i2.hookFirstSync("resolveFileUrl", [{ chunkId: f2, fileName: t3, format: s2, moduleId: p2, referenceId: h2, relativePath: n3 }]) || no[s2](n3);
      return void e4.overwrite(l2.start, l2.end, r3, { contentOnly: true });
    }
    let m2 = i2.hookFirstSync("resolveImportMeta", [a2, { chunkId: f2, format: s2, moduleId: p2 }]);
    m2 || (m2 = (_a2 = ro[s2]) == null ? void 0 : _a2.call(ro, a2, { chunkId: f2, snippets: n2 }), t2.accessedDocumentCurrentScript || (t2.accessedDocumentCurrentScript = Yr.includes(s2) && "undefined" !== m2)), "string" == typeof m2 && (l2 instanceof jn ? e4.overwrite(l2.start, l2.end, m2, { contentOnly: true }) : e4.overwrite(u2, d2, m2, { contentOnly: true }));
  }
  setResolution(e4, t2, s2) {
    var _a2;
    this.preliminaryChunkId = s2;
    const i2 = (((_a2 = this.metaProperty) == null ? void 0 : _a2.startsWith(Hr)) ? Qr : Xr)[e4];
    i2.length > 0 && this.scope.addAccessedGlobals(i2, t2);
  }
}, MethodDefinition: Yn, NewExpression: class extends ei {
  hasEffects(e4) {
    try {
      for (const t2 of this.arguments)
        if (t2.hasEffects(e4))
          return true;
      return !this.annotationPure && (this.callee.hasEffects(e4) || this.callee.hasEffectsOnInteractionAtPath(X, this.interaction, e4));
    } finally {
      this.deoptimized || this.applyDeoptimizations();
    }
  }
  hasEffectsOnInteractionAtPath(e4, { type: t2 }) {
    return e4.length > 0 || 0 !== t2;
  }
  include(e4, t2) {
    this.deoptimized || this.applyDeoptimizations(), t2 ? super.include(e4, t2) : (this.included = true, this.callee.include(e4, false)), this.callee.includeCallArguments(e4, this.arguments);
  }
  initialise() {
    this.interaction = { args: [null, ...this.arguments], type: 2, withNew: true };
  }
  render(e4, t2) {
    this.callee.render(e4, t2), Vn(e4, t2, this);
  }
  applyDeoptimizations() {
    this.deoptimized = true, this.callee.deoptimizeArgumentsOnInteractionAtPath(this.interaction, X, se), this.context.requestTreeshakingPass();
  }
}, ObjectExpression: class extends ei {
  constructor() {
    super(...arguments), this.objectEntity = null;
  }
  deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2) {
    this.getObjectEntity().deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2);
  }
  deoptimizeCache() {
    this.getObjectEntity().deoptimizeAllProperties();
  }
  deoptimizePath(e4) {
    this.getObjectEntity().deoptimizePath(e4);
  }
  getLiteralValueAtPath(e4, t2, s2) {
    return this.getObjectEntity().getLiteralValueAtPath(e4, t2, s2);
  }
  getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2) {
    return this.getObjectEntity().getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2);
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    return this.getObjectEntity().hasEffectsOnInteractionAtPath(e4, t2, s2);
  }
  render(e4, t2, { renderedSurroundingElement: s2 } = me) {
    super.render(e4, t2), s2 !== Rs && s2 !== ks || (e4.appendRight(this.start, "("), e4.prependLeft(this.end, ")"));
  }
  applyDeoptimizations() {
  }
  getObjectEntity() {
    if (null !== this.objectEntity)
      return this.objectEntity;
    let e4 = ui;
    const t2 = [];
    for (const s2 of this.properties) {
      if (s2 instanceof ti) {
        t2.push({ key: q, kind: "init", property: s2 });
        continue;
      }
      let i2;
      if (s2.computed) {
        const e5 = s2.key.getLiteralValueAtPath(X, se, this);
        if ("symbol" == typeof e5) {
          t2.push({ key: q, kind: s2.kind, property: s2 });
          continue;
        }
        i2 = String(e5);
      } else if (i2 = s2.key instanceof hn ? s2.key.name : String(s2.key.value), "__proto__" === i2 && "init" === s2.kind) {
        e4 = s2.value instanceof Bn && null === s2.value.value ? null : s2.value;
        continue;
      }
      t2.push({ key: i2, kind: s2.kind, property: s2 });
    }
    return this.objectEntity = new li(t2, e4);
  }
}, ObjectPattern: _n, PrivateIdentifier: class extends ei {
}, Program: oo, Property: class extends Kn {
  constructor() {
    super(...arguments), this.declarationInit = null;
  }
  declare(e4, t2) {
    return this.declarationInit = t2, this.value.declare(e4, ae);
  }
  hasEffects(e4) {
    this.deoptimized || this.applyDeoptimizations();
    const t2 = this.context.options.treeshake.propertyReadSideEffects;
    return "ObjectPattern" === this.parent.type && "always" === t2 || this.key.hasEffects(e4) || this.value.hasEffects(e4);
  }
  markDeclarationReached() {
    this.value.markDeclarationReached();
  }
  render(e4, t2) {
    this.shorthand || this.key.render(e4, t2), this.value.render(e4, t2, { isShorthandProperty: this.shorthand });
  }
  applyDeoptimizations() {
    this.deoptimized = true, null !== this.declarationInit && (this.declarationInit.deoptimizePath([q, q]), this.context.requestTreeshakingPass());
  }
}, PropertyDefinition: class extends ei {
  deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2) {
    var _a2;
    (_a2 = this.value) == null ? void 0 : _a2.deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2);
  }
  deoptimizePath(e4) {
    var _a2;
    (_a2 = this.value) == null ? void 0 : _a2.deoptimizePath(e4);
  }
  getLiteralValueAtPath(e4, t2, s2) {
    return this.value ? this.value.getLiteralValueAtPath(e4, t2, s2) : ne;
  }
  getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2) {
    return this.value ? this.value.getReturnExpressionWhenCalledAtPath(e4, t2, s2, i2) : le;
  }
  hasEffects(e4) {
    var _a2;
    return this.key.hasEffects(e4) || this.static && !!((_a2 = this.value) == null ? void 0 : _a2.hasEffects(e4));
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    return !this.value || this.value.hasEffectsOnInteractionAtPath(e4, t2, s2);
  }
  applyDeoptimizations() {
  }
}, RestElement: In, ReturnStatement: class extends ei {
  hasEffects(e4) {
    var _a2;
    return !(e4.ignore.returnYield && !((_a2 = this.argument) == null ? void 0 : _a2.hasEffects(e4))) || (e4.brokenFlow = true, false);
  }
  include(e4, t2) {
    var _a2;
    this.included = true, (_a2 = this.argument) == null ? void 0 : _a2.include(e4, t2), e4.brokenFlow = true;
  }
  initialise() {
    this.scope.addReturnExpression(this.argument || ae);
  }
  render(e4, t2) {
    this.argument && (this.argument.render(e4, t2, { preventASI: true }), this.argument.start === this.start + 6 && e4.prependLeft(this.start + 6, " "));
  }
}, SequenceExpression: class extends ei {
  deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2) {
    this.expressions[this.expressions.length - 1].deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2);
  }
  deoptimizePath(e4) {
    this.expressions[this.expressions.length - 1].deoptimizePath(e4);
  }
  getLiteralValueAtPath(e4, t2, s2) {
    return this.expressions[this.expressions.length - 1].getLiteralValueAtPath(e4, t2, s2);
  }
  hasEffects(e4) {
    for (const t2 of this.expressions)
      if (t2.hasEffects(e4))
        return true;
    return false;
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    return this.expressions[this.expressions.length - 1].hasEffectsOnInteractionAtPath(e4, t2, s2);
  }
  include(e4, t2) {
    this.included = true;
    const s2 = this.expressions[this.expressions.length - 1];
    for (const i2 of this.expressions)
      (t2 || i2 === s2 && !(this.parent instanceof An) || i2.shouldBeIncluded(e4)) && i2.include(e4, t2);
  }
  render(e4, t2, { renderedParentType: s2, isCalleeOfRenderedParent: i2, preventASI: n2 } = me) {
    let r2 = 0, o2 = null;
    const a2 = this.expressions[this.expressions.length - 1];
    for (const { node: l2, separator: c2, start: h2, end: u2 } of bn(this.expressions, e4, this.start, this.end))
      if (l2.included)
        if (r2++, o2 = c2, 1 === r2 && n2 && vn(e4, h2, l2.start), 1 === r2) {
          const n3 = s2 || this.parent.type;
          l2.render(e4, t2, { isCalleeOfRenderedParent: i2 && l2 === a2, renderedParentType: n3, renderedSurroundingElement: n3 });
        } else
          l2.render(e4, t2);
      else
        dn(l2, e4, h2, u2);
    o2 && e4.remove(o2, this.end);
  }
}, SpreadElement: ti, StaticBlock: class extends ei {
  createScope(e4) {
    this.scope = new Sn(e4);
  }
  hasEffects(e4) {
    for (const t2 of this.body)
      if (t2.hasEffects(e4))
        return true;
    return false;
  }
  include(e4, t2) {
    this.included = true;
    for (const s2 of this.body)
      (t2 || s2.shouldBeIncluded(e4)) && s2.include(e4, t2);
  }
  render(e4, t2) {
    if (this.body.length > 0) {
      const s2 = mn(e4.original.slice(this.start, this.end), "{") + 1;
      En(this.body, e4, this.start + s2, this.end - 1, t2);
    } else
      super.render(e4, t2);
  }
}, Super: class extends ei {
  bind() {
    this.variable = this.scope.findVariable("this");
  }
  deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2) {
    this.variable.deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2);
  }
  deoptimizePath(e4) {
    this.variable.deoptimizePath(e4);
  }
  include() {
    this.included || (this.included = true, this.context.includeVariableInModule(this.variable));
  }
}, SwitchCase: ao, SwitchStatement: class extends ei {
  createScope(e4) {
    this.parentScope = e4, this.scope = new Sn(e4);
  }
  hasEffects(e4) {
    if (this.discriminant.hasEffects(e4))
      return true;
    const { brokenFlow: t2, hasBreak: s2, ignore: i2 } = e4, { breaks: n2 } = i2;
    i2.breaks = true, e4.hasBreak = false;
    let r2 = true;
    for (const s3 of this.cases) {
      if (s3.hasEffects(e4))
        return true;
      r2 && (r2 = e4.brokenFlow && !e4.hasBreak), e4.hasBreak = false, e4.brokenFlow = t2;
    }
    return null !== this.defaultCase && (e4.brokenFlow = r2), i2.breaks = n2, e4.hasBreak = s2, false;
  }
  include(e4, t2) {
    this.included = true, this.discriminant.include(e4, t2);
    const { brokenFlow: s2, hasBreak: i2 } = e4;
    e4.hasBreak = false;
    let n2 = true, r2 = t2 || null !== this.defaultCase && this.defaultCase < this.cases.length - 1;
    for (let i3 = this.cases.length - 1; i3 >= 0; i3--) {
      const o2 = this.cases[i3];
      if (o2.included && (r2 = true), !r2) {
        const e5 = is();
        e5.ignore.breaks = true, r2 = o2.hasEffects(e5);
      }
      r2 ? (o2.include(e4, t2), n2 && (n2 = e4.brokenFlow && !e4.hasBreak), e4.hasBreak = false, e4.brokenFlow = s2) : n2 = s2;
    }
    r2 && null !== this.defaultCase && (e4.brokenFlow = n2), e4.hasBreak = i2;
  }
  initialise() {
    for (let e4 = 0; e4 < this.cases.length; e4++)
      if (null === this.cases[e4].test)
        return void (this.defaultCase = e4);
    this.defaultCase = null;
  }
  parseNode(e4) {
    this.discriminant = new (this.context.getNodeConstructor(e4.discriminant.type))(e4.discriminant, this, this.parentScope), super.parseNode(e4);
  }
  render(e4, t2) {
    this.discriminant.render(e4, t2), this.cases.length > 0 && En(this.cases, e4, this.cases[0].start, this.end - 1, t2);
  }
}, TaggedTemplateExpression: class extends Gn {
  bind() {
    if (super.bind(), this.tag.type === Os) {
      const e4 = this.tag.name;
      this.scope.findVariable(e4).isNamespace && this.context.log(Ae, Ot(e4), this.start);
    }
  }
  hasEffects(e4) {
    try {
      for (const t2 of this.quasi.expressions)
        if (t2.hasEffects(e4))
          return true;
      return this.tag.hasEffects(e4) || this.tag.hasEffectsOnInteractionAtPath(X, this.interaction, e4);
    } finally {
      this.deoptimized || this.applyDeoptimizations();
    }
  }
  include(e4, t2) {
    this.deoptimized || this.applyDeoptimizations(), t2 ? super.include(e4, t2) : (this.included = true, this.tag.include(e4, t2), this.quasi.include(e4, t2)), this.tag.includeCallArguments(e4, this.args);
    const [s2] = this.getReturnExpression();
    s2.included || s2.include(e4, false);
  }
  initialise() {
    this.args = [ae, ...this.quasi.expressions], this.interaction = { args: [this.tag instanceof jn && !this.tag.variable ? this.tag.object : null, ...this.args], type: 2, withNew: false };
  }
  render(e4, t2) {
    this.tag.render(e4, t2, { isCalleeOfRenderedParent: true }), this.quasi.render(e4, t2);
  }
  applyDeoptimizations() {
    this.deoptimized = true, this.tag.deoptimizeArgumentsOnInteractionAtPath(this.interaction, X, se), this.context.requestTreeshakingPass();
  }
  getReturnExpression(e4 = se) {
    return null === this.returnExpression ? (this.returnExpression = le, this.returnExpression = this.tag.getReturnExpressionWhenCalledAtPath(X, this.interaction, e4, this)) : this.returnExpression;
  }
}, TemplateElement: class extends ei {
  bind() {
  }
  hasEffects() {
    return false;
  }
  include() {
    this.included = true;
  }
  parseNode(e4) {
    this.value = e4.value, super.parseNode(e4);
  }
  render() {
  }
}, TemplateLiteral: lo, ThisExpression: class extends ei {
  bind() {
    this.variable = this.scope.findVariable("this");
  }
  deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2) {
    this.variable.deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2);
  }
  deoptimizePath(e4) {
    this.variable.deoptimizePath(e4);
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    return 0 === e4.length ? 0 !== t2.type : this.variable.hasEffectsOnInteractionAtPath(e4, t2, s2);
  }
  include() {
    this.included || (this.included = true, this.context.includeVariableInModule(this.variable));
  }
  initialise() {
    this.alias = this.scope.findLexicalBoundary() instanceof uo ? this.context.moduleContext : null, "undefined" === this.alias && this.context.log(Ae, { code: "THIS_IS_UNDEFINED", message: "The 'this' keyword is equivalent to 'undefined' at the top level of an ES module, and has been rewritten", url: De("troubleshooting/#error-this-is-undefined") }, this.start);
  }
  render(e4) {
    null !== this.alias && e4.overwrite(this.start, this.end, this.alias, { contentOnly: false, storeName: true });
  }
}, ThrowStatement: class extends ei {
  hasEffects() {
    return true;
  }
  include(e4, t2) {
    this.included = true, this.argument.include(e4, t2), e4.brokenFlow = true;
  }
  render(e4, t2) {
    this.argument.render(e4, t2, { preventASI: true }), this.argument.start === this.start + 5 && e4.prependLeft(this.start + 5, " ");
  }
}, TryStatement: class extends ei {
  constructor() {
    super(...arguments), this.directlyIncluded = false, this.includedLabelsAfterBlock = null;
  }
  hasEffects(e4) {
    var _a2;
    return (this.context.options.treeshake.tryCatchDeoptimization ? this.block.body.length > 0 : this.block.hasEffects(e4)) || !!((_a2 = this.finalizer) == null ? void 0 : _a2.hasEffects(e4));
  }
  include(e4, t2) {
    var _a2, _b;
    const s2 = (_a2 = this.context.options.treeshake) == null ? void 0 : _a2.tryCatchDeoptimization, { brokenFlow: i2, includedLabels: n2 } = e4;
    if (this.directlyIncluded && s2) {
      if (this.includedLabelsAfterBlock)
        for (const e5 of this.includedLabelsAfterBlock)
          n2.add(e5);
    } else
      this.included = true, this.directlyIncluded = true, this.block.include(e4, s2 ? Js : t2), n2.size > 0 && (this.includedLabelsAfterBlock = [...n2]), e4.brokenFlow = i2;
    null !== this.handler && (this.handler.include(e4, t2), e4.brokenFlow = i2), (_b = this.finalizer) == null ? void 0 : _b.include(e4, t2);
  }
}, UnaryExpression: class extends ei {
  getLiteralValueAtPath(e4, t2, s2) {
    if (e4.length > 0)
      return ne;
    const i2 = this.argument.getLiteralValueAtPath(X, t2, s2);
    return "symbol" == typeof i2 ? ne : po[this.operator](i2);
  }
  hasEffects(e4) {
    return this.deoptimized || this.applyDeoptimizations(), !("typeof" === this.operator && this.argument instanceof hn) && (this.argument.hasEffects(e4) || "delete" === this.operator && this.argument.hasEffectsOnInteractionAtPath(X, ue, e4));
  }
  hasEffectsOnInteractionAtPath(e4, { type: t2 }) {
    return 0 !== t2 || e4.length > ("void" === this.operator ? 0 : 1);
  }
  applyDeoptimizations() {
    this.deoptimized = true, "delete" === this.operator && (this.argument.deoptimizePath(X), this.context.requestTreeshakingPass());
  }
}, UnknownNode: class extends ei {
  hasEffects() {
    return true;
  }
  include(e4) {
    super.include(e4, true);
  }
}, UpdateExpression: class extends ei {
  hasEffects(e4) {
    return this.deoptimized || this.applyDeoptimizations(), this.argument.hasEffectsAsAssignmentTarget(e4, true);
  }
  hasEffectsOnInteractionAtPath(e4, { type: t2 }) {
    return e4.length > 1 || 0 !== t2;
  }
  include(e4, t2) {
    this.deoptimized || this.applyDeoptimizations(), this.included = true, this.argument.includeAsAssignmentTarget(e4, t2, true);
  }
  initialise() {
    this.argument.setAssignedValue(ae);
  }
  render(e4, t2) {
    const { exportNamesByVariable: s2, format: i2, snippets: { _: n2 } } = t2;
    if (this.argument.render(e4, t2), "system" === i2) {
      const i3 = this.argument.variable, r2 = s2.get(i3);
      if (r2)
        if (this.prefix)
          1 === r2.length ? $n(i3, this.start, this.end, e4, t2) : Nn(i3, this.start, this.end, this.parent.type !== Rs, e4, t2);
        else {
          const s3 = this.operator[0];
          !function(e5, t3, s4, i4, n3, r3, o2) {
            const { _: a2 } = r3.snippets;
            n3.prependRight(t3, `${Cn([e5], r3, o2)},${a2}`), i4 && (n3.prependRight(t3, "("), n3.appendLeft(s4, ")"));
          }(i3, this.start, this.end, this.parent.type !== Rs, e4, t2, `${n2}${s3}${n2}1`);
        }
    }
  }
  applyDeoptimizations() {
    if (this.deoptimized = true, this.argument.deoptimizePath(X), this.argument instanceof hn) {
      this.scope.findVariable(this.argument.name).isReassigned = true;
    }
    this.context.requestTreeshakingPass();
  }
}, VariableDeclaration: fo, VariableDeclarator: jr, WhileStatement: class extends ei {
  hasEffects(e4) {
    return !!this.test.hasEffects(e4) || tr(e4, this.body);
  }
  include(e4, t2) {
    this.included = true, this.test.include(e4, t2), sr(e4, this.body, t2);
  }
}, YieldExpression: class extends ei {
  hasEffects(e4) {
    var _a2;
    return this.deoptimized || this.applyDeoptimizations(), !(e4.ignore.returnYield && !((_a2 = this.argument) == null ? void 0 : _a2.hasEffects(e4)));
  }
  render(e4, t2) {
    this.argument && (this.argument.render(e4, t2, { preventASI: true }), this.argument.start === this.start + 5 && e4.prependLeft(this.start + 5, " "));
  }
} }, go = "_missingExportShim";
class yo extends pe {
  constructor(e4) {
    super(go), this.module = e4;
  }
  include() {
    super.include(), this.module.needsExportShim = true;
  }
}
class xo extends pe {
  constructor(e4) {
    super(e4.getModuleName()), this.memberVariables = null, this.mergedNamespaces = [], this.referencedEarly = false, this.references = [], this.context = e4, this.module = e4.module;
  }
  addReference(e4) {
    this.references.push(e4), this.name = e4.name;
  }
  deoptimizeArgumentsOnInteractionAtPath(e4, t2, s2) {
    var _a2;
    if (t2.length > 1 || 1 === t2.length && 2 === e4.type) {
      const i2 = t2[0];
      "string" == typeof i2 ? (_a2 = this.getMemberVariables()[i2]) == null ? void 0 : _a2.deoptimizeArgumentsOnInteractionAtPath(e4, t2.slice(1), s2) : ce(e4);
    }
  }
  deoptimizePath(e4) {
    var _a2;
    if (e4.length > 1) {
      const t2 = e4[0];
      "string" == typeof t2 && ((_a2 = this.getMemberVariables()[t2]) == null ? void 0 : _a2.deoptimizePath(e4.slice(1)));
    }
  }
  getLiteralValueAtPath(e4) {
    return e4[0] === Y ? "Module" : ne;
  }
  getMemberVariables() {
    if (this.memberVariables)
      return this.memberVariables;
    const e4 = /* @__PURE__ */ Object.create(null), t2 = [...this.context.getExports(), ...this.context.getReexports()].sort();
    for (const s2 of t2)
      if ("*" !== s2[0] && s2 !== this.module.info.syntheticNamedExports) {
        const t3 = this.context.traceExport(s2);
        t3 && (e4[s2] = t3);
      }
    return this.memberVariables = e4;
  }
  hasEffectsOnInteractionAtPath(e4, t2, s2) {
    const { type: i2 } = t2;
    if (0 === e4.length)
      return true;
    if (1 === e4.length && 2 !== i2)
      return 1 === i2;
    const n2 = e4[0];
    if ("string" != typeof n2)
      return true;
    const r2 = this.getMemberVariables()[n2];
    return !r2 || r2.hasEffectsOnInteractionAtPath(e4.slice(1), t2, s2);
  }
  include() {
    this.included = true, this.context.includeAllExports();
  }
  prepare(e4) {
    this.mergedNamespaces.length > 0 && this.module.scope.addAccessedGlobals([Er], e4);
  }
  renderBlock(e4) {
    const { exportNamesByVariable: t2, format: s2, freeze: i2, indent: n2, namespaceToStringTag: r2, snippets: { _: o2, cnst: a2, getObject: l2, getPropertyAccess: c2, n: h2, s: u2 } } = e4, d2 = this.getMemberVariables(), p2 = Object.entries(d2).filter(([e5, t3]) => t3.included).map(([e5, t3]) => this.referencedEarly || t3.isReassigned || t3 === this ? [null, `get ${e5}${o2}()${o2}{${o2}return ${t3.getName(c2)}${u2}${o2}}`] : [e5, t3.getName(c2)]);
    p2.unshift([null, `__proto__:${o2}null`]);
    let f2 = l2(p2, { lineBreakIndent: { base: "", t: n2 } });
    if (this.mergedNamespaces.length > 0) {
      const e5 = this.mergedNamespaces.map((e6) => e6.getName(c2));
      f2 = `/*#__PURE__*/${Er}(${f2},${o2}[${e5.join(`,${o2}`)}])`;
    } else
      r2 && (f2 = `/*#__PURE__*/Object.defineProperty(${f2},${o2}Symbol.toStringTag,${o2}${zr(l2)})`), i2 && (f2 = `/*#__PURE__*/Object.freeze(${f2})`);
    return f2 = `${a2} ${this.getName(c2)}${o2}=${o2}${f2};`, "system" === s2 && t2.has(this) && (f2 += `${h2}${Cn([this], e4)};`), f2;
  }
  renderFirst() {
    return this.referencedEarly;
  }
  setMergedNamespaces(e4) {
    this.mergedNamespaces = e4;
    const t2 = this.context.getModuleExecIndex();
    for (const e5 of this.references)
      if (e5.context.getModuleExecIndex() <= t2) {
        this.referencedEarly = true;
        break;
      }
  }
}
xo.prototype.isNamespace = true;
class Eo extends pe {
  constructor(e4, t2, s2) {
    super(t2), this.baseVariable = null, this.context = e4, this.module = e4.module, this.syntheticNamespace = s2;
  }
  getBaseVariable() {
    if (this.baseVariable)
      return this.baseVariable;
    let e4 = this.syntheticNamespace;
    for (; e4 instanceof ho || e4 instanceof Eo; ) {
      if (e4 instanceof ho) {
        const t2 = e4.getOriginalVariable();
        if (t2 === e4)
          break;
        e4 = t2;
      }
      e4 instanceof Eo && (e4 = e4.syntheticNamespace);
    }
    return this.baseVariable = e4;
  }
  getBaseVariableName() {
    return this.syntheticNamespace.getBaseVariableName();
  }
  getName(e4) {
    return `${this.syntheticNamespace.getName(e4)}${e4(this.name)}`;
  }
  include() {
    this.included = true, this.context.includeVariableInModule(this.syntheticNamespace);
  }
  setRenderNames(e4, t2) {
    super.setRenderNames(e4, t2);
  }
}
var bo;
!function(e4) {
  e4[e4.LOAD_AND_PARSE = 0] = "LOAD_AND_PARSE", e4[e4.ANALYSE = 1] = "ANALYSE", e4[e4.GENERATE = 2] = "GENERATE";
}(bo || (bo = {}));
const vo = /* @__PURE__ */ new WeakMap();
function So(e4, t2) {
  if (e4) {
    const t3 = vo.get(e4);
    t3 && function(e5) {
      void 0 === e5.encodedMappings && e5.decodedMappings && (e5.encodedMappings = i.encode(e5.decodedMappings)), e5.decodedMappings = void 0;
    }(t3);
  }
  if (t2)
    for (const e5 of t2)
      e5.missing || So(e5);
}
function Ao(e4) {
  if (!e4)
    return null;
  if ("string" == typeof e4 && (e4 = JSON.parse(e4)), !e4.mappings)
    return { mappings: [], names: [], sources: [], version: 3 };
  const t2 = e4.mappings, s2 = Array.isArray(t2), n2 = { decodedMappings: s2 ? t2 : void 0, encodedMappings: s2 ? void 0 : t2 }, r2 = { ...e4, get mappings() {
    return n2.decodedMappings || (n2.decodedMappings = n2.encodedMappings ? i.decode(n2.encodedMappings) : [], n2.encodedMappings = void 0), n2.decodedMappings;
  } };
  return vo.set(r2, n2), r2;
}
function ko(e4) {
  return e4.id;
}
const Io = (e4) => {
  const t2 = e4.key;
  return t2 && (t2.name || t2.value);
};
function wo(e4, t2) {
  const s2 = Object.keys(e4);
  return s2.length !== Object.keys(t2).length || s2.some((s3) => e4[s3] !== t2[s3]);
}
var Po = "performance" in ("undefined" == typeof globalThis ? "undefined" == typeof window ? {} : window : globalThis) ? performance : { now: () => 0 }, Co = { memoryUsage: () => ({ heapUsed: 0 }) };
let $o = /* @__PURE__ */ new Map();
function No(e4, t2) {
  switch (t2) {
    case 1:
      return `# ${e4}`;
    case 2:
      return `## ${e4}`;
    case 3:
      return e4;
    default:
      return `${"  ".repeat(t2 - 4)}- ${e4}`;
  }
}
function _o(e4, t2 = 3) {
  e4 = No(e4, t2);
  const s2 = Co.memoryUsage().heapUsed, i2 = Po.now(), n2 = $o.get(e4);
  void 0 === n2 ? $o.set(e4, { memory: 0, startMemory: s2, startTime: i2, time: 0, totalMemory: 0 }) : (n2.startMemory = s2, n2.startTime = i2);
}
function Ro(e4, t2 = 3) {
  e4 = No(e4, t2);
  const s2 = $o.get(e4);
  if (void 0 !== s2) {
    const e5 = Co.memoryUsage().heapUsed;
    s2.memory += e5 - s2.startMemory, s2.time += Po.now() - s2.startTime, s2.totalMemory = Math.max(s2.totalMemory, e5);
  }
}
function Mo() {
  const e4 = {};
  for (const [t2, { memory: s2, time: i2, totalMemory: n2 }] of $o)
    e4[t2] = [i2, s2, n2];
  return e4;
}
let Oo = Ui, Do = Ui;
const Lo = ["augmentChunkHash", "buildEnd", "buildStart", "generateBundle", "load", "moduleParsed", "options", "outputOptions", "renderChunk", "renderDynamicImport", "renderStart", "resolveDynamicImport", "resolveFileUrl", "resolveId", "resolveImportMeta", "shouldTransformCachedModule", "transform", "writeBundle"];
function To(e4, t2) {
  if (e4._hasTimer)
    return e4;
  e4._hasTimer = true;
  for (const s2 of Lo)
    if (s2 in e4) {
      let i2 = `plugin ${t2}`;
      e4.name && (i2 += ` (${e4.name})`), i2 += ` - ${s2}`;
      const n2 = function(...e5) {
        Oo(i2, 4);
        const t3 = r2.apply(this, e5);
        return Do(i2, 4), t3;
      };
      let r2;
      "function" == typeof e4[s2].handler ? (r2 = e4[s2].handler, e4[s2].handler = n2) : (r2 = e4[s2], e4[s2] = n2);
    }
  return e4;
}
function Vo(e4) {
  e4.isExecuted = true;
  const t2 = [e4], s2 = /* @__PURE__ */ new Set();
  for (const e5 of t2)
    for (const i2 of [...e5.dependencies, ...e5.implicitlyLoadedBefore])
      i2 instanceof Jt || i2.isExecuted || !i2.info.moduleSideEffects && !e5.implicitlyLoadedBefore.has(i2) || s2.has(i2.id) || (i2.isExecuted = true, s2.add(i2.id), t2.push(i2));
}
const Bo = { identifier: null, localName: go };
function zo(e4, t2, s2, i2, n2 = /* @__PURE__ */ new Map()) {
  const r2 = n2.get(t2);
  if (r2) {
    if (r2.has(e4))
      return i2 ? [null] : Qe((o2 = t2, a2 = e4.id, { code: rt, exporter: a2, message: `"${o2}" cannot be exported from "${V(a2)}" as it is a reexport that references itself.` }));
    r2.add(e4);
  } else
    n2.set(t2, /* @__PURE__ */ new Set([e4]));
  var o2, a2;
  return e4.getVariableForExportName(t2, { importerForSideEffects: s2, isExportAllSearch: i2, searchedNamesAndModules: n2 });
}
function Fo(e4, t2) {
  const s2 = U(t2.sideEffectDependenciesByVariable, e4, G);
  let i2 = e4;
  const n2 = /* @__PURE__ */ new Set([i2]);
  for (; ; ) {
    const e5 = i2.module;
    if (i2 = i2 instanceof ho ? i2.getDirectOriginalVariable() : i2 instanceof Eo ? i2.syntheticNamespace : null, !i2 || n2.has(i2))
      break;
    n2.add(i2), s2.add(e5);
    const t3 = e5.sideEffectDependenciesByVariable.get(i2);
    if (t3)
      for (const e6 of t3)
        s2.add(e6);
  }
  return s2;
}
class jo {
  constructor(e4, t2, s2, i2, n2, r2, o2, a2) {
    this.graph = e4, this.id = t2, this.options = s2, this.alternativeReexportModules = /* @__PURE__ */ new Map(), this.chunkFileNames = /* @__PURE__ */ new Set(), this.chunkNames = [], this.cycles = /* @__PURE__ */ new Set(), this.dependencies = /* @__PURE__ */ new Set(), this.dynamicDependencies = /* @__PURE__ */ new Set(), this.dynamicImporters = [], this.dynamicImports = [], this.execIndex = 1 / 0, this.implicitlyLoadedAfter = /* @__PURE__ */ new Set(), this.implicitlyLoadedBefore = /* @__PURE__ */ new Set(), this.importDescriptions = /* @__PURE__ */ new Map(), this.importMetas = [], this.importedFromNotTreeshaken = false, this.importers = [], this.includedDynamicImporters = [], this.includedImports = /* @__PURE__ */ new Set(), this.isExecuted = false, this.isUserDefinedEntryPoint = false, this.needsExportShim = false, this.sideEffectDependenciesByVariable = /* @__PURE__ */ new Map(), this.sourcesWithAssertions = /* @__PURE__ */ new Map(), this.allExportNames = null, this.ast = null, this.exportAllModules = [], this.exportAllSources = /* @__PURE__ */ new Set(), this.exportNamesByVariable = null, this.exportShimVariable = new yo(this), this.exports = /* @__PURE__ */ new Map(), this.namespaceReexportsByName = /* @__PURE__ */ new Map(), this.reexportDescriptions = /* @__PURE__ */ new Map(), this.relevantDependencies = null, this.syntheticExports = /* @__PURE__ */ new Map(), this.syntheticNamespace = null, this.transformDependencies = [], this.transitiveReexports = null, this.excludeFromSourcemap = /\0/.test(t2), this.context = s2.moduleContext(t2), this.preserveSignature = this.options.preserveEntrySignatures;
    const l2 = this, { dynamicImports: c2, dynamicImporters: h2, exportAllSources: u2, exports: d2, implicitlyLoadedAfter: p2, implicitlyLoadedBefore: f2, importers: m2, reexportDescriptions: g2, sourcesWithAssertions: y2 } = this;
    this.info = { assertions: a2, ast: null, code: null, get dynamicallyImportedIdResolutions() {
      return c2.map(({ argument: e5 }) => "string" == typeof e5 && l2.resolvedIds[e5]).filter(Boolean);
    }, get dynamicallyImportedIds() {
      return c2.map(({ id: e5 }) => e5).filter((e5) => null != e5);
    }, get dynamicImporters() {
      return h2.sort();
    }, get exportedBindings() {
      const e5 = { ".": [...d2.keys()] };
      for (const [t3, { source: s3 }] of g2)
        (e5[s3] ?? (e5[s3] = [])).push(t3);
      for (const t3 of u2)
        (e5[t3] ?? (e5[t3] = [])).push("*");
      return e5;
    }, get exports() {
      return [...d2.keys(), ...g2.keys(), ...[...u2].map(() => "*")];
    }, get hasDefaultExport() {
      return l2.ast ? l2.exports.has("default") || g2.has("default") : null;
    }, get hasModuleSideEffects() {
      return Qt("Accessing ModuleInfo.hasModuleSideEffects from plugins is deprecated. Please use ModuleInfo.moduleSideEffects instead.", Xe, true, s2), this.moduleSideEffects;
    }, id: t2, get implicitlyLoadedAfterOneOf() {
      return Array.from(p2, ko).sort();
    }, get implicitlyLoadedBefore() {
      return Array.from(f2, ko).sort();
    }, get importedIdResolutions() {
      return Array.from(y2.keys(), (e5) => l2.resolvedIds[e5]).filter(Boolean);
    }, get importedIds() {
      return Array.from(y2.keys(), (e5) => {
        var _a2;
        return (_a2 = l2.resolvedIds[e5]) == null ? void 0 : _a2.id;
      }).filter(Boolean);
    }, get importers() {
      return m2.sort();
    }, isEntry: i2, isExternal: false, get isIncluded() {
      return e4.phase !== bo.GENERATE ? null : l2.isIncluded();
    }, meta: { ...o2 }, moduleSideEffects: n2, syntheticNamedExports: r2 }, Object.defineProperty(this.info, "hasModuleSideEffects", { enumerable: false });
  }
  basename() {
    const e4 = C(this.id), t2 = N(this.id);
    return Se(t2 ? e4.slice(0, -t2.length) : e4);
  }
  bindReferences() {
    this.ast.bind();
  }
  error(e4, t2) {
    return this.addLocationToLogProps(e4, t2), Qe(e4);
  }
  estimateSize() {
    let e4 = 0;
    for (const t2 of this.ast.body)
      t2.included && (e4 += t2.end - t2.start);
    return e4;
  }
  getAllExportNames() {
    if (this.allExportNames)
      return this.allExportNames;
    this.allExportNames = /* @__PURE__ */ new Set([...this.exports.keys(), ...this.reexportDescriptions.keys()]);
    for (const e4 of this.exportAllModules)
      if (e4 instanceof Jt)
        this.allExportNames.add(`*${e4.id}`);
      else
        for (const t2 of e4.getAllExportNames())
          "default" !== t2 && this.allExportNames.add(t2);
    return "string" == typeof this.info.syntheticNamedExports && this.allExportNames.delete(this.info.syntheticNamedExports), this.allExportNames;
  }
  getDependenciesToBeIncluded() {
    if (this.relevantDependencies)
      return this.relevantDependencies;
    this.relevantDependencies = /* @__PURE__ */ new Set();
    const e4 = /* @__PURE__ */ new Set(), t2 = /* @__PURE__ */ new Set(), s2 = new Set(this.includedImports);
    if (this.info.isEntry || this.includedDynamicImporters.length > 0 || this.namespace.included || this.implicitlyLoadedAfter.size > 0)
      for (const e5 of [...this.getReexports(), ...this.getExports()]) {
        const [t3] = this.getVariableForExportName(e5);
        (t3 == null ? void 0 : t3.included) && s2.add(t3);
      }
    for (let i2 of s2) {
      const s3 = this.sideEffectDependenciesByVariable.get(i2);
      if (s3)
        for (const e5 of s3)
          t2.add(e5);
      i2 instanceof Eo ? i2 = i2.getBaseVariable() : i2 instanceof ho && (i2 = i2.getOriginalVariable()), e4.add(i2.module);
    }
    if (this.options.treeshake && "no-treeshake" !== this.info.moduleSideEffects)
      this.addRelevantSideEffectDependencies(this.relevantDependencies, e4, t2);
    else
      for (const e5 of this.dependencies)
        this.relevantDependencies.add(e5);
    for (const t3 of e4)
      this.relevantDependencies.add(t3);
    return this.relevantDependencies;
  }
  getExportNamesByVariable() {
    if (this.exportNamesByVariable)
      return this.exportNamesByVariable;
    const e4 = /* @__PURE__ */ new Map();
    for (const t2 of this.getAllExportNames()) {
      let [s2] = this.getVariableForExportName(t2);
      if (s2 instanceof ho && (s2 = s2.getOriginalVariable()), !s2 || !(s2.included || s2 instanceof fe))
        continue;
      const i2 = e4.get(s2);
      i2 ? i2.push(t2) : e4.set(s2, [t2]);
    }
    return this.exportNamesByVariable = e4;
  }
  getExports() {
    return [...this.exports.keys()];
  }
  getReexports() {
    if (this.transitiveReexports)
      return this.transitiveReexports;
    this.transitiveReexports = [];
    const e4 = new Set(this.reexportDescriptions.keys());
    for (const t2 of this.exportAllModules)
      if (t2 instanceof Jt)
        e4.add(`*${t2.id}`);
      else
        for (const s2 of [...t2.getReexports(), ...t2.getExports()])
          "default" !== s2 && e4.add(s2);
    return this.transitiveReexports = [...e4];
  }
  getRenderedExports() {
    const e4 = [], t2 = [];
    for (const s2 of this.exports.keys()) {
      const [i2] = this.getVariableForExportName(s2);
      (i2 && i2.included ? e4 : t2).push(s2);
    }
    return { removedExports: t2, renderedExports: e4 };
  }
  getSyntheticNamespace() {
    return null === this.syntheticNamespace && (this.syntheticNamespace = void 0, [this.syntheticNamespace] = this.getVariableForExportName("string" == typeof this.info.syntheticNamedExports ? this.info.syntheticNamedExports : "default", { onlyExplicit: true })), this.syntheticNamespace ? this.syntheticNamespace : Qe((e4 = this.id, t2 = this.info.syntheticNamedExports, { code: "SYNTHETIC_NAMED_EXPORTS_NEED_NAMESPACE_EXPORT", exporter: e4, message: `Module "${V(e4)}" that is marked with \`syntheticNamedExports: ${JSON.stringify(t2)}\` needs ${"string" == typeof t2 && "default" !== t2 ? `an explicit export named "${t2}"` : "a default export"} that does not reexport an unresolved named export of the same module.` }));
    var e4, t2;
  }
  getVariableForExportName(e4, { importerForSideEffects: t2, isExportAllSearch: s2, onlyExplicit: i2, searchedNamesAndModules: n2 } = ge) {
    if ("*" === e4[0]) {
      if (1 === e4.length)
        return [this.namespace];
      return this.graph.modulesById.get(e4.slice(1)).getVariableForExportName("*");
    }
    const r2 = this.reexportDescriptions.get(e4);
    if (r2) {
      const [e5] = zo(r2.module, r2.localName, t2, false, n2);
      return e5 ? (t2 && (Uo(e5, t2, this), this.info.moduleSideEffects && U(t2.sideEffectDependenciesByVariable, e5, G).add(this)), [e5]) : this.error(Ut(r2.localName, this.id, r2.module.id), r2.start);
    }
    const o2 = this.exports.get(e4);
    if (o2) {
      if (o2 === Bo)
        return [this.exportShimVariable];
      const e5 = o2.localName, s3 = this.traceVariable(e5, { importerForSideEffects: t2, searchedNamesAndModules: n2 });
      return t2 && (Uo(s3, t2, this), U(t2.sideEffectDependenciesByVariable, s3, G).add(this)), [s3];
    }
    if (i2)
      return [null];
    if ("default" !== e4) {
      const s3 = this.namespaceReexportsByName.get(e4) ?? this.getVariableFromNamespaceReexports(e4, t2, n2);
      if (this.namespaceReexportsByName.set(e4, s3), s3[0])
        return s3;
    }
    return this.info.syntheticNamedExports ? [U(this.syntheticExports, e4, () => new Eo(this.astContext, e4, this.getSyntheticNamespace()))] : !s2 && this.options.shimMissingExports ? (this.shimMissingExport(e4), [this.exportShimVariable]) : [null];
  }
  hasEffects() {
    return "no-treeshake" === this.info.moduleSideEffects || this.ast.hasCachedEffects();
  }
  include() {
    const e4 = ss();
    this.ast.shouldBeIncluded(e4) && this.ast.include(e4, false);
  }
  includeAllExports(e4) {
    this.isExecuted || (Vo(this), this.graph.needsTreeshakingPass = true);
    for (const t2 of this.exports.keys())
      if (e4 || t2 !== this.info.syntheticNamedExports) {
        const e5 = this.getVariableForExportName(t2)[0];
        e5.deoptimizePath(Q), e5.included || this.includeVariable(e5);
      }
    for (const e5 of this.getReexports()) {
      const [t2] = this.getVariableForExportName(e5);
      t2 && (t2.deoptimizePath(Q), t2.included || this.includeVariable(t2), t2 instanceof fe && (t2.module.reexported = true));
    }
    e4 && this.namespace.setMergedNamespaces(this.includeAndGetAdditionalMergedNamespaces());
  }
  includeAllInBundle() {
    this.ast.include(ss(), true), this.includeAllExports(false);
  }
  includeExportsByNames(e4) {
    this.isExecuted || (Vo(this), this.graph.needsTreeshakingPass = true);
    let t2 = false;
    for (const s2 of e4) {
      const e5 = this.getVariableForExportName(s2)[0];
      e5 && (e5.deoptimizePath(Q), e5.included || this.includeVariable(e5)), this.exports.has(s2) || this.reexportDescriptions.has(s2) || (t2 = true);
    }
    t2 && this.namespace.setMergedNamespaces(this.includeAndGetAdditionalMergedNamespaces());
  }
  isIncluded() {
    return this.ast && (this.ast.included || this.namespace.included || this.importedFromNotTreeshaken || this.exportShimVariable.included);
  }
  linkImports() {
    this.addModulesToImportDescriptions(this.importDescriptions), this.addModulesToImportDescriptions(this.reexportDescriptions);
    const e4 = [];
    for (const t2 of this.exportAllSources) {
      const s2 = this.graph.modulesById.get(this.resolvedIds[t2].id);
      s2 instanceof Jt ? e4.push(s2) : this.exportAllModules.push(s2);
    }
    this.exportAllModules.push(...e4);
  }
  log(e4, t2, s2) {
    this.addLocationToLogProps(t2, s2), this.options.onLog(e4, t2);
  }
  render(e4) {
    const t2 = this.magicString.clone();
    this.ast.render(t2, e4), t2.trim();
    const { usesTopLevelAwait: s2 } = this.astContext;
    return s2 && "es" !== e4.format && "system" !== e4.format ? Qe((i2 = this.id, n2 = e4.format, { code: "INVALID_TLA_FORMAT", id: i2, message: `Module format "${n2}" does not support top-level await. Use the "es" or "system" output formats rather.` })) : { source: t2, usesTopLevelAwait: s2 };
    var i2, n2;
  }
  setSource({ ast: e4, code: t2, customTransformCache: s2, originalCode: i2, originalSourcemap: n2, resolvedIds: r2, sourcemapChain: o2, transformDependencies: a2, transformFiles: l2, ...c2 }) {
    Oo("generate ast", 3), this.info.code = t2, this.originalCode = i2, this.originalSourcemap = Ao(n2), this.sourcemapChain = o2.map((e5) => e5.missing ? e5 : Ao(e5)), So(this.originalSourcemap, this.sourcemapChain), l2 && (this.transformFiles = l2), this.transformDependencies = a2, this.customTransformCache = s2, this.updateOptions(c2);
    const h2 = e4 ?? this.tryParse();
    Do("generate ast", 3), Oo("analyze ast", 3), this.resolvedIds = r2 ?? /* @__PURE__ */ Object.create(null);
    const u2 = this.id;
    this.magicString = new y(t2, { filename: this.excludeFromSourcemap ? null : u2, indentExclusionRanges: [] }), this.astContext = { addDynamicImport: this.addDynamicImport.bind(this), addExport: this.addExport.bind(this), addImport: this.addImport.bind(this), addImportMeta: this.addImportMeta.bind(this), code: t2, deoptimizationTracker: this.graph.deoptimizationTracker, error: this.error.bind(this), fileName: u2, getExports: this.getExports.bind(this), getModuleExecIndex: () => this.execIndex, getModuleName: this.basename.bind(this), getNodeConstructor: (e5) => mo[e5] || mo.UnknownNode, getReexports: this.getReexports.bind(this), importDescriptions: this.importDescriptions, includeAllExports: () => this.includeAllExports(true), includeDynamicImport: this.includeDynamicImport.bind(this), includeVariableInModule: this.includeVariableInModule.bind(this), log: this.log.bind(this), magicString: this.magicString, manualPureFunctions: this.graph.pureFunctions, module: this, moduleContext: this.context, options: this.options, requestTreeshakingPass: () => this.graph.needsTreeshakingPass = true, traceExport: (e5) => this.getVariableForExportName(e5)[0], traceVariable: this.traceVariable.bind(this), usesTopLevelAwait: false }, this.scope = new uo(this.graph.scope, this.astContext), this.namespace = new xo(this.astContext), this.ast = new oo(h2, { context: this.astContext, type: "Module" }, this.scope), e4 || false !== this.options.cache ? this.info.ast = h2 : Object.defineProperty(this.info, "ast", { get: () => {
      if (this.graph.astLru.has(u2))
        return this.graph.astLru.get(u2);
      {
        const e5 = this.tryParse();
        return this.graph.astLru.set(u2, e5), e5;
      }
    } }), Do("analyze ast", 3);
  }
  toJSON() {
    return { assertions: this.info.assertions, ast: this.info.ast, code: this.info.code, customTransformCache: this.customTransformCache, dependencies: Array.from(this.dependencies, ko), id: this.id, meta: this.info.meta, moduleSideEffects: this.info.moduleSideEffects, originalCode: this.originalCode, originalSourcemap: this.originalSourcemap, resolvedIds: this.resolvedIds, sourcemapChain: this.sourcemapChain, syntheticNamedExports: this.info.syntheticNamedExports, transformDependencies: this.transformDependencies, transformFiles: this.transformFiles };
  }
  traceVariable(e4, { importerForSideEffects: t2, isExportAllSearch: s2, searchedNamesAndModules: i2 } = ge) {
    const n2 = this.scope.variables.get(e4);
    if (n2)
      return n2;
    const r2 = this.importDescriptions.get(e4);
    if (r2) {
      const e5 = r2.module;
      if (e5 instanceof jo && "*" === r2.name)
        return e5.namespace;
      const [n3] = zo(e5, r2.name, t2 || this, s2, i2);
      return n3 || this.error(Ut(r2.name, this.id, e5.id), r2.start);
    }
    return null;
  }
  updateOptions({ meta: e4, moduleSideEffects: t2, syntheticNamedExports: s2 }) {
    null != t2 && (this.info.moduleSideEffects = t2), null != s2 && (this.info.syntheticNamedExports = s2), null != e4 && Object.assign(this.info.meta, e4);
  }
  addDynamicImport(e4) {
    let t2 = e4.source;
    t2 instanceof lo ? 1 === t2.quasis.length && t2.quasis[0].value.cooked && (t2 = t2.quasis[0].value.cooked) : t2 instanceof Bn && "string" == typeof t2.value && (t2 = t2.value), this.dynamicImports.push({ argument: t2, id: null, node: e4, resolution: null });
  }
  addExport(e4) {
    if (e4 instanceof rr)
      this.exports.set("default", { identifier: e4.variable.getAssignedVariableName(), localName: "default" });
    else if (e4 instanceof ir) {
      const t2 = e4.source.value;
      if (this.addSource(t2, e4), e4.exported) {
        const s2 = e4.exported.name;
        this.reexportDescriptions.set(s2, { localName: "*", module: null, source: t2, start: e4.start });
      } else
        this.exportAllSources.add(t2);
    } else if (e4.source instanceof Bn) {
      const t2 = e4.source.value;
      this.addSource(t2, e4);
      for (const { exported: s2, local: i2, start: n2 } of e4.specifiers) {
        const e5 = s2 instanceof Bn ? s2.value : s2.name;
        this.reexportDescriptions.set(e5, { localName: i2 instanceof Bn ? i2.value : i2.name, module: null, source: t2, start: n2 });
      }
    } else if (e4.declaration) {
      const t2 = e4.declaration;
      if (t2 instanceof fo)
        for (const e5 of t2.declarations)
          for (const t3 of ts(e5.id))
            this.exports.set(t3, { identifier: null, localName: t3 });
      else {
        const e5 = t2.id.name;
        this.exports.set(e5, { identifier: null, localName: e5 });
      }
    } else
      for (const { local: t2, exported: s2 } of e4.specifiers) {
        const e5 = t2.name, i2 = s2 instanceof hn ? s2.name : s2.value;
        this.exports.set(i2, { identifier: null, localName: e5 });
      }
  }
  addImport(e4) {
    const t2 = e4.source.value;
    this.addSource(t2, e4);
    for (const s2 of e4.specifiers) {
      const e5 = s2 instanceof dr ? "default" : s2 instanceof qr ? "*" : s2.imported instanceof hn ? s2.imported.name : s2.imported.value;
      this.importDescriptions.set(s2.local.name, { module: null, name: e5, source: t2, start: s2.start });
    }
  }
  addImportMeta(e4) {
    this.importMetas.push(e4);
  }
  addLocationToLogProps(e4, t2) {
    e4.id = this.id, e4.pos = t2;
    let s2 = this.info.code;
    const i2 = Ce(s2, t2, { offsetLine: 1 });
    if (i2) {
      let { column: n2, line: r2 } = i2;
      try {
        ({ column: n2, line: r2 } = function(e5, t3) {
          const s3 = e5.filter((e6) => !e6.missing);
          e:
            for (; s3.length > 0; ) {
              const e6 = s3.pop().mappings[t3.line - 1];
              if (e6) {
                const s4 = e6.filter((e7) => e7.length > 1), i3 = s4[s4.length - 1];
                for (const e7 of s4)
                  if (e7[0] >= t3.column || e7 === i3) {
                    t3 = { column: e7[3], line: e7[2] + 1 };
                    continue e;
                  }
              }
              throw new Error("Can't resolve original location of error.");
            }
          return t3;
        }(this.sourcemapChain, { column: n2, line: r2 })), s2 = this.originalCode;
      } catch (e5) {
        this.options.onLog(Ae, function(e6, t3, s3, i3, n3) {
          return { cause: e6, code: "SOURCEMAP_ERROR", id: t3, loc: { column: s3, file: t3, line: i3 }, message: `Error when using sourcemap for reporting an error: ${e6.message}`, pos: n3 };
        }(e5, this.id, n2, r2, t2));
      }
      Ze(e4, { column: n2, line: r2 }, s2, this.id);
    }
  }
  addModulesToImportDescriptions(e4) {
    for (const t2 of e4.values()) {
      const { id: e5 } = this.resolvedIds[t2.source];
      t2.module = this.graph.modulesById.get(e5);
    }
  }
  addRelevantSideEffectDependencies(e4, t2, s2) {
    const i2 = /* @__PURE__ */ new Set(), n2 = (r2) => {
      for (const o2 of r2)
        i2.has(o2) || (i2.add(o2), t2.has(o2) ? e4.add(o2) : (o2.info.moduleSideEffects || s2.has(o2)) && (o2 instanceof Jt || o2.hasEffects() ? e4.add(o2) : n2(o2.dependencies)));
    };
    n2(this.dependencies), n2(s2);
  }
  addSource(e4, t2) {
    const s2 = (i2 = t2.assertions, (i2 == null ? void 0 : i2.length) ? Object.fromEntries(i2.map((e5) => [Io(e5), e5.value.value])) : ge);
    var i2;
    const n2 = this.sourcesWithAssertions.get(e4);
    n2 ? wo(n2, s2) && this.log(Ae, Bt(n2, s2, e4, this.id), t2.start) : this.sourcesWithAssertions.set(e4, s2);
  }
  getVariableFromNamespaceReexports(e4, t2, s2) {
    let i2 = null;
    const n2 = /* @__PURE__ */ new Map(), r2 = /* @__PURE__ */ new Set();
    for (const o3 of this.exportAllModules) {
      if (o3.info.syntheticNamedExports === e4)
        continue;
      const [a3, l3] = zo(o3, e4, t2, true, Go(s2));
      o3 instanceof Jt || l3 ? r2.add(a3) : a3 instanceof Eo ? i2 || (i2 = a3) : a3 && n2.set(a3, o3);
    }
    if (n2.size > 0) {
      const t3 = [...n2], s3 = t3[0][0];
      return 1 === t3.length ? [s3] : (this.options.onLog(Ae, (o2 = e4, a2 = this.id, l2 = t3.map(([, e5]) => e5.id), { binding: o2, code: "NAMESPACE_CONFLICT", ids: l2, message: `Conflicting namespaces: "${V(a2)}" re-exports "${o2}" from one of the modules ${Oe(l2.map((e5) => V(e5)))} (will be ignored).`, reexporter: a2 })), [null]);
    }
    var o2, a2, l2;
    if (r2.size > 0) {
      const t3 = [...r2], s3 = t3[0];
      return t3.length > 1 && this.options.onLog(Ae, function(e5, t4, s4, i3) {
        return { binding: e5, code: "AMBIGUOUS_EXTERNAL_NAMESPACES", ids: i3, message: `Ambiguous external namespace resolution: "${V(t4)}" re-exports "${e5}" from one of the external modules ${Oe(i3.map((e6) => V(e6)))}, guessing "${V(s4)}".`, reexporter: t4 };
      }(e4, this.id, s3.module.id, t3.map((e5) => e5.module.id))), [s3, true];
    }
    return i2 ? [i2] : [null];
  }
  includeAndGetAdditionalMergedNamespaces() {
    const e4 = /* @__PURE__ */ new Set(), t2 = /* @__PURE__ */ new Set();
    for (const s2 of [this, ...this.exportAllModules])
      if (s2 instanceof Jt) {
        const [t3] = s2.getVariableForExportName("*");
        t3.include(), this.includedImports.add(t3), e4.add(t3);
      } else if (s2.info.syntheticNamedExports) {
        const e5 = s2.getSyntheticNamespace();
        e5.include(), this.includedImports.add(e5), t2.add(e5);
      }
    return [...t2, ...e4];
  }
  includeDynamicImport(e4) {
    const t2 = this.dynamicImports.find((t3) => t3.node === e4).resolution;
    if (t2 instanceof jo) {
      t2.includedDynamicImporters.push(this);
      const s2 = this.options.treeshake ? e4.getDeterministicImportedNames() : void 0;
      s2 ? t2.includeExportsByNames(s2) : t2.includeAllExports(true);
    }
  }
  includeVariable(e4) {
    const t2 = e4.module;
    if (e4.included)
      t2 instanceof jo && t2 !== this && Fo(e4, this);
    else if (e4.include(), this.graph.needsTreeshakingPass = true, t2 instanceof jo && (t2.isExecuted || Vo(t2), t2 !== this)) {
      const t3 = Fo(e4, this);
      for (const e5 of t3)
        e5.isExecuted || Vo(e5);
    }
  }
  includeVariableInModule(e4) {
    this.includeVariable(e4);
    const t2 = e4.module;
    t2 && t2 !== this && this.includedImports.add(e4);
  }
  shimMissingExport(e4) {
    var t2, s2;
    this.options.onLog(Ae, (t2 = this.id, { binding: s2 = e4, code: "SHIMMED_EXPORT", exporter: t2, message: `Missing export "${s2}" has been shimmed in module "${V(t2)}".` })), this.exports.set(e4, Bo);
  }
  tryParse() {
    try {
      return this.graph.contextParse(this.info.code);
    } catch (e4) {
      return this.error(function(e5, t2) {
        let s2 = e5.message.replace(/ \(\d+:\d+\)$/, "");
        return t2.endsWith(".json") ? s2 += " (Note that you need @rollup/plugin-json to import JSON files)" : t2.endsWith(".js") || (s2 += " (Note that you need plugins to import files that are not JavaScript)"), { cause: e5, code: "PARSE_ERROR", id: t2, message: s2 };
      }(e4, this.id), e4.pos);
    }
  }
}
function Uo(e4, t2, s2) {
  if (e4.module instanceof jo && e4.module !== s2) {
    const i2 = e4.module.cycles;
    if (i2.size > 0) {
      const n2 = s2.cycles;
      for (const r2 of n2)
        if (i2.has(r2)) {
          t2.alternativeReexportModules.set(e4, s2);
          break;
        }
    }
  }
}
const Go = (e4) => e4 && new Map(Array.from(e4, ([e5, t2]) => [e5, new Set(t2)]));
function Wo(e4) {
  return e4.endsWith(".js") ? e4.slice(0, -3) : e4;
}
function qo(e4, t2) {
  return e4.autoId ? `${e4.basePath ? e4.basePath + "/" : ""}${Wo(t2)}` : e4.id ?? "";
}
function Ho(e4, t2, s2, i2, n2, r2, o2, a2 = "return ") {
  const { _: l2, getDirectReturnFunction: c2, getFunctionIntro: h2, getPropertyAccess: u2, n: d2, s: p2 } = n2;
  if (!s2)
    return `${d2}${d2}${a2}${function(e5, t3, s3, i3, n3) {
      if (e5.length > 0)
        return e5[0].local;
      for (const { defaultVariableName: e6, importPath: r3, isChunk: o3, name: a3, namedExportsMode: l3, namespaceVariableName: c3, reexports: h3 } of t3)
        if (h3)
          return Ko(a3, h3[0].imported, l3, o3, e6, c3, s3, r3, i3, n3);
    }(e4, t2, i2, o2, u2)};`;
  let f2 = "";
  for (const { defaultVariableName: e5, importPath: n3, isChunk: a3, name: h3, namedExportsMode: p3, namespaceVariableName: m2, reexports: g2 } of t2)
    if (g2 && s2) {
      for (const t3 of g2)
        if ("*" !== t3.reexported) {
          const s3 = Ko(h3, t3.imported, p3, a3, e5, m2, i2, n3, o2, u2);
          if (f2 && (f2 += d2), "*" !== t3.imported && t3.needsLiveBinding) {
            const [e6, i3] = c2([], { functionReturn: true, lineBreakIndent: null, name: null });
            f2 += `Object.defineProperty(exports,${l2}'${t3.reexported}',${l2}{${d2}${r2}enumerable:${l2}true,${d2}${r2}get:${l2}${e6}${s3}${i3}${d2}});`;
          } else
            f2 += `exports${u2(t3.reexported)}${l2}=${l2}${s3};`;
        }
    }
  for (const { exported: t3, local: s3 } of e4) {
    const e5 = `exports${u2(t3)}`;
    e5 !== s3 && (f2 && (f2 += d2), f2 += `${e5}${l2}=${l2}${s3};`);
  }
  for (const { name: e5, reexports: i3 } of t2)
    if (i3 && s2) {
      for (const t3 of i3)
        if ("*" === t3.reexported) {
          f2 && (f2 += d2);
          const s3 = `{${d2}${r2}if${l2}(k${l2}!==${l2}'default'${l2}&&${l2}!Object.prototype.hasOwnProperty.call(exports,${l2}k))${l2}${Qo(e5, t3.needsLiveBinding, r2, n2)}${p2}${d2}}`;
          f2 += `Object.keys(${e5}).forEach(${h2(["k"], { isAsync: false, name: null })}${s3});`;
        }
    }
  return f2 ? `${d2}${d2}${f2}` : "";
}
function Ko(e4, t2, s2, i2, n2, r2, o2, a2, l2, c2) {
  if ("default" === t2) {
    if (!i2) {
      const t3 = o2(a2), s3 = vr[t3] ? n2 : e4;
      return Sr(t3, l2) ? `${s3}${c2("default")}` : s3;
    }
    return s2 ? `${e4}${c2("default")}` : e4;
  }
  return "*" === t2 ? (i2 ? !s2 : Ar[o2(a2)]) ? r2 : e4 : `${e4}${c2(t2)}`;
}
function Yo(e4) {
  return e4([["value", "true"]], { lineBreakIndent: null });
}
function Xo(e4, t2, s2, { _: i2, getObject: n2 }) {
  if (e4) {
    if (t2)
      return s2 ? `Object.defineProperties(exports,${i2}${n2([["__esModule", Yo(n2)], [null, `[Symbol.toStringTag]:${i2}${zr(n2)}`]], { lineBreakIndent: null })});` : `Object.defineProperty(exports,${i2}'__esModule',${i2}${Yo(n2)});`;
    if (s2)
      return `Object.defineProperty(exports,${i2}Symbol.toStringTag,${i2}${zr(n2)});`;
  }
  return "";
}
const Qo = (e4, t2, s2, { _: i2, getDirectReturnFunction: n2, n: r2 }) => {
  if (t2) {
    const [t3, o2] = n2([], { functionReturn: true, lineBreakIndent: null, name: null });
    return `Object.defineProperty(exports,${i2}k,${i2}{${r2}${s2}${s2}enumerable:${i2}true,${r2}${s2}${s2}get:${i2}${t3}${e4}[k]${o2}${r2}${s2}})`;
  }
  return `exports[k]${i2}=${i2}${e4}[k]`;
};
function Zo(e4, t2, s2, i2, n2, r2, o2, a2) {
  const { _: l2, cnst: c2, n: h2 } = a2, u2 = /* @__PURE__ */ new Set(), d2 = [], p2 = (e5, t3, s3) => {
    u2.add(t3), d2.push(`${c2} ${e5}${l2}=${l2}/*#__PURE__*/${t3}(${s3});`);
  };
  for (const { defaultVariableName: s3, imports: i3, importPath: n3, isChunk: r3, name: o3, namedExportsMode: a3, namespaceVariableName: l3, reexports: c3 } of e4)
    if (r3) {
      for (const { imported: e5, reexported: t3 } of [...i3 || [], ...c3 || []])
        if ("*" === e5 && "*" !== t3) {
          a3 || p2(l3, xr, o3);
          break;
        }
    } else {
      const e5 = t2(n3);
      let r4 = false, a4 = false;
      for (const { imported: t3, reexported: n4 } of [...i3 || [], ...c3 || []]) {
        let i4, c4;
        "default" === t3 ? r4 || (r4 = true, s3 !== l3 && (c4 = s3, i4 = vr[e5])) : "*" !== t3 || "*" === n4 || a4 || (a4 = true, i4 = Ar[e5], c4 = l3), i4 && p2(c4, i4, o3);
      }
    }
  return `${Ir(u2, r2, o2, a2, s2, i2, n2)}${d2.length > 0 ? `${d2.join(h2)}${h2}${h2}` : ""}`;
}
function Jo(e4, t2) {
  return "." !== e4[0] ? e4 : t2 ? (s2 = e4).endsWith(".js") ? s2 : s2 + ".js" : Wo(e4);
  var s2;
}
const ea = /* @__PURE__ */ new Set([...t(["assert", "async_hooks", "buffer", "child_process", "cluster", "console", "constants", "crypto", "dgram", "diagnostics_channel", "dns", "domain", "events", "fs", "http", "http2", "https", "inspector", "module", "net", "os", "path", "perf_hooks", "process", "punycode", "querystring", "readline", "repl", "stream", "string_decoder", "timers", "tls", "trace_events", "tty", "url", "util", "v8", "vm", "wasi", "worker_threads", "zlib"]), "assert/strict", "dns/promises", "fs/promises", "path/posix", "path/win32", "readline/promises", "stream/consumers", "stream/promises", "stream/web", "timers/promises", "util/types"]);
function ta(e4, t2) {
  const s2 = t2.map(({ importPath: e5 }) => e5).filter((e5) => ea.has(e5) || e5.startsWith("node:"));
  0 !== s2.length && e4(Ae, function(e5) {
    return { code: vt, ids: e5, message: `Creating a browser bundle that depends on Node.js built-in modules (${Oe(e5)}). You might need to include https://github.com/FredKSchott/rollup-plugin-polyfill-node` };
  }(s2));
}
const sa = (e4, t2) => e4.split(".").map(t2).join("");
function ia(e4, t2, s2, i2, { _: n2, getPropertyAccess: r2 }) {
  const o2 = e4.split(".");
  o2[0] = ("function" == typeof s2 ? s2(o2[0]) : s2[o2[0]]) || o2[0];
  const a2 = o2.pop();
  let l2 = t2, c2 = [...o2.map((e5) => (l2 += r2(e5), `${l2}${n2}=${n2}${l2}${n2}||${n2}{}`)), `${l2}${r2(a2)}`].join(`,${n2}`) + `${n2}=${n2}${i2}`;
  return o2.length > 0 && (c2 = `(${c2})`), c2;
}
function na(e4) {
  let t2 = e4.length;
  for (; t2--; ) {
    const { imports: s2, reexports: i2 } = e4[t2];
    if (s2 || i2)
      return e4.slice(0, t2 + 1);
  }
  return [];
}
const ra = ({ dependencies: e4, exports: t2 }) => {
  const s2 = new Set(t2.map((e5) => e5.exported));
  s2.add("default");
  for (const { reexports: t3 } of e4)
    if (t3)
      for (const e5 of t3)
        "*" !== e5.reexported && s2.add(e5.reexported);
  return s2;
}, oa = (e4, t2, { _: s2, cnst: i2, getObject: n2, n: r2 }) => e4 ? `${r2}${t2}${i2} _starExcludes${s2}=${s2}${n2([...e4].map((e5) => [e5, "1"]), { lineBreakIndent: { base: t2, t: t2 } })};` : "", aa = (e4, t2, { _: s2, n: i2 }) => e4.length > 0 ? `${i2}${t2}var ${e4.join(`,${s2}`)};` : "", la = (e4, t2, s2) => ca(e4.filter((e5) => e5.hoisted).map((e5) => ({ name: e5.exported, value: e5.local })), t2, s2);
function ca(e4, t2, { _: s2, n: i2 }) {
  return 0 === e4.length ? "" : 1 === e4.length ? `exports('${e4[0].name}',${s2}${e4[0].value});${i2}${i2}` : `exports({${i2}` + e4.map(({ name: e5, value: i3 }) => `${t2}${e5}:${s2}${i3}`).join(`,${i2}`) + `${i2}});${i2}${i2}`;
}
const ha = (e4, t2, s2) => ca(e4.filter((e5) => e5.expression).map((e5) => ({ name: e5.exported, value: e5.local })), t2, s2), ua = (e4, t2, s2) => ca(e4.filter((e5) => e5.local === go).map((e5) => ({ name: e5.exported, value: go })), t2, s2);
function da(e4, t2, s2) {
  return e4 ? `${t2}${sa(e4, s2)}` : "null";
}
var pa = { amd: function(e4, { accessedGlobals: t2, dependencies: s2, exports: i2, hasDefaultExport: n2, hasExports: r2, id: o2, indent: a2, intro: l2, isEntryFacade: c2, isModuleFacade: h2, namedExportsMode: u2, log: d2, outro: p2, snippets: f2 }, { amd: m2, esModule: g2, externalLiveBindings: y2, freeze: x2, interop: E2, namespaceToStringTag: b2, strict: v2 }) {
  ta(d2, s2);
  const S2 = s2.map((e5) => `'${Jo(e5.importPath, m2.forceJsExtensionForImports)}'`), A2 = s2.map((e5) => e5.name), { n: k2, getNonArrowFunctionIntro: I2, _: w2 } = f2;
  u2 && r2 && (A2.unshift("exports"), S2.unshift("'exports'")), t2.has("require") && (A2.unshift("require"), S2.unshift("'require'")), t2.has("module") && (A2.unshift("module"), S2.unshift("'module'"));
  const P2 = qo(m2, o2), C2 = (P2 ? `'${P2}',${w2}` : "") + (S2.length > 0 ? `[${S2.join(`,${w2}`)}],${w2}` : ""), $2 = v2 ? `${w2}'use strict';` : "";
  e4.prepend(`${l2}${Zo(s2, E2, y2, x2, b2, t2, a2, f2)}`);
  const N2 = Ho(i2, s2, u2, E2, f2, a2, y2);
  let _2 = Xo(u2 && r2, c2 && (true === g2 || "if-default-prop" === g2 && n2), h2 && b2, f2);
  _2 && (_2 = k2 + k2 + _2), e4.append(`${N2}${_2}${p2}`).indent(a2).prepend(`${m2.define}(${C2}(${I2(A2, { isAsync: false, name: null })}{${$2}${k2}${k2}`).append(`${k2}${k2}}));`);
}, cjs: function(e4, { accessedGlobals: t2, dependencies: s2, exports: i2, hasDefaultExport: n2, hasExports: r2, indent: o2, intro: a2, isEntryFacade: l2, isModuleFacade: c2, namedExportsMode: h2, outro: u2, snippets: d2 }, { compact: p2, esModule: f2, externalLiveBindings: m2, freeze: g2, interop: y2, namespaceToStringTag: x2, strict: E2 }) {
  const { _: b2, n: v2 } = d2, S2 = E2 ? `'use strict';${v2}${v2}` : "";
  let A2 = Xo(h2 && r2, l2 && (true === f2 || "if-default-prop" === f2 && n2), c2 && x2, d2);
  A2 && (A2 += v2 + v2);
  const k2 = function(e5, { _: t3, cnst: s3, n: i3 }, n3) {
    let r3 = "", o3 = false;
    for (const { importPath: a3, name: l3, reexports: c3, imports: h3 } of e5)
      c3 || h3 ? (r3 += n3 && o3 ? "," : `${r3 ? `;${i3}` : ""}${s3} `, o3 = true, r3 += `${l3}${t3}=${t3}require('${a3}')`) : (r3 && (r3 += n3 && !o3 ? "," : `;${i3}`), o3 = false, r3 += `require('${a3}')`);
    if (r3)
      return `${r3};${i3}${i3}`;
    return "";
  }(s2, d2, p2), I2 = Zo(s2, y2, m2, g2, x2, t2, o2, d2);
  e4.prepend(`${S2}${a2}${A2}${k2}${I2}`);
  const w2 = Ho(i2, s2, h2, y2, d2, o2, m2, `module.exports${b2}=${b2}`);
  e4.append(`${w2}${u2}`);
}, es: function(e4, { accessedGlobals: t2, indent: s2, intro: i2, outro: n2, dependencies: r2, exports: o2, snippets: a2 }, { externalLiveBindings: l2, freeze: c2, namespaceToStringTag: h2 }) {
  const { n: u2 } = a2, d2 = function(e5, { _: t3 }) {
    const s3 = [];
    for (const { importPath: i3, reexports: n3, imports: r3, name: o3, assertions: a3 } of e5) {
      const e6 = `'${i3}'${a3 ? `${t3}assert${t3}${a3}` : ""};`;
      if (n3 || r3) {
        if (r3) {
          let i4 = null, n4 = null;
          const o4 = [];
          for (const e7 of r3)
            "default" === e7.imported ? i4 = e7 : "*" === e7.imported ? n4 = e7 : o4.push(e7);
          n4 && s3.push(`import${t3}*${t3}as ${n4.local} from${t3}${e6}`), i4 && 0 === o4.length ? s3.push(`import ${i4.local} from${t3}${e6}`) : o4.length > 0 && s3.push(`import ${i4 ? `${i4.local},${t3}` : ""}{${t3}${o4.map((e7) => e7.imported === e7.local ? e7.imported : `${e7.imported} as ${e7.local}`).join(`,${t3}`)}${t3}}${t3}from${t3}${e6}`);
        }
        if (n3) {
          let i4 = null;
          const a4 = [], l3 = [];
          for (const e7 of n3)
            "*" === e7.reexported ? i4 = e7 : "*" === e7.imported ? a4.push(e7) : l3.push(e7);
          if (i4 && s3.push(`export${t3}*${t3}from${t3}${e6}`), a4.length > 0) {
            r3 && r3.some((e7) => "*" === e7.imported && e7.local === o3) || s3.push(`import${t3}*${t3}as ${o3} from${t3}${e6}`);
            for (const e7 of a4)
              s3.push(`export${t3}{${t3}${o3 === e7.reexported ? o3 : `${o3} as ${e7.reexported}`} };`);
          }
          l3.length > 0 && s3.push(`export${t3}{${t3}${l3.map((e7) => e7.imported === e7.reexported ? e7.imported : `${e7.imported} as ${e7.reexported}`).join(`,${t3}`)}${t3}}${t3}from${t3}${e6}`);
        }
      } else
        s3.push(`import${t3}${e6}`);
    }
    return s3;
  }(r2, a2);
  d2.length > 0 && (i2 += d2.join(u2) + u2 + u2), (i2 += Ir(null, t2, s2, a2, l2, c2, h2)) && e4.prepend(i2);
  const p2 = function(e5, { _: t3, cnst: s3 }) {
    const i3 = [], n3 = [];
    for (const r3 of e5)
      r3.expression && i3.push(`${s3} ${r3.local}${t3}=${t3}${r3.expression};`), n3.push(r3.exported === r3.local ? r3.local : `${r3.local} as ${r3.exported}`);
    n3.length > 0 && i3.push(`export${t3}{${t3}${n3.join(`,${t3}`)}${t3}};`);
    return i3;
  }(o2, a2);
  p2.length > 0 && e4.append(u2 + u2 + p2.join(u2).trim()), n2 && e4.append(n2), e4.trim();
}, iife: function(e4, { accessedGlobals: t2, dependencies: s2, exports: i2, hasDefaultExport: n2, hasExports: r2, indent: o2, intro: a2, namedExportsMode: l2, log: c2, outro: h2, snippets: u2 }, { compact: d2, esModule: p2, extend: f2, freeze: m2, externalLiveBindings: g2, globals: y2, interop: x2, name: E2, namespaceToStringTag: b2, strict: v2 }) {
  const { _: S2, getNonArrowFunctionIntro: A2, getPropertyAccess: k2, n: I2 } = u2, w2 = E2 && E2.includes("."), P2 = !f2 && !w2;
  if (E2 && P2 && (ve(C2 = E2) || be.test(C2)))
    return Qe(function(e5) {
      return { code: ct, message: `Given name "${e5}" is not a legal JS identifier. If you need this, you can try "output.extend: true".`, url: De(Fe) };
    }(E2));
  var C2;
  ta(c2, s2);
  const $2 = na(s2), N2 = $2.map((e5) => e5.globalName || "null"), _2 = $2.map((e5) => e5.name);
  r2 && !E2 && c2(Ae, { code: bt, message: 'If you do not supply "output.name", you may not be able to access the exports of an IIFE bundle.', url: De(Ke) }), l2 && r2 && (f2 ? (N2.unshift(`this${sa(E2, k2)}${S2}=${S2}this${sa(E2, k2)}${S2}||${S2}{}`), _2.unshift("exports")) : (N2.unshift("{}"), _2.unshift("exports")));
  const R2 = v2 ? `${o2}'use strict';${I2}` : "", M2 = Zo(s2, x2, g2, m2, b2, t2, o2, u2);
  e4.prepend(`${a2}${M2}`);
  let O2 = `(${A2(_2, { isAsync: false, name: null })}{${I2}${R2}${I2}`;
  r2 && (!E2 || f2 && l2 || (O2 = (P2 ? `var ${E2}` : `this${sa(E2, k2)}`) + `${S2}=${S2}${O2}`), w2 && (O2 = function(e5, t3, s3, { _: i3, getPropertyAccess: n3, s: r3 }, o3) {
    const a3 = e5.split(".");
    a3[0] = ("function" == typeof s3 ? s3(a3[0]) : s3[a3[0]]) || a3[0], a3.pop();
    let l3 = t3;
    return a3.map((e6) => (l3 += n3(e6), `${l3}${i3}=${i3}${l3}${i3}||${i3}{}${r3}`)).join(o3 ? "," : "\n") + (o3 && a3.length > 0 ? ";" : "\n");
  }(E2, "this", y2, u2, d2) + O2));
  let D2 = `${I2}${I2}})(${N2.join(`,${S2}`)});`;
  r2 && !f2 && l2 && (D2 = `${I2}${I2}${o2}return exports;${D2}`);
  const L2 = Ho(i2, s2, l2, x2, u2, o2, g2);
  let T2 = Xo(l2 && r2, true === p2 || "if-default-prop" === p2 && n2, b2, u2);
  T2 && (T2 = I2 + I2 + T2), e4.append(`${L2}${T2}${h2}`).indent(o2).prepend(O2).append(D2);
}, system: function(e4, { accessedGlobals: t2, dependencies: s2, exports: i2, hasExports: n2, indent: r2, intro: o2, snippets: a2, outro: l2, usesTopLevelAwait: c2 }, { externalLiveBindings: h2, freeze: u2, name: d2, namespaceToStringTag: p2, strict: f2, systemNullSetters: m2 }) {
  const { _: g2, getFunctionIntro: y2, getNonArrowFunctionIntro: x2, n: E2, s: b2 } = a2, { importBindings: v2, setters: S2, starExcludes: A2 } = function(e5, t3, s3, { _: i3, cnst: n3, getObject: r3, getPropertyAccess: o3, n: a3 }) {
    const l3 = [], c3 = [];
    let h3 = null;
    for (const { imports: u3, reexports: d3 } of e5) {
      const p3 = [];
      if (u3)
        for (const e6 of u3)
          l3.push(e6.local), "*" === e6.imported ? p3.push(`${e6.local}${i3}=${i3}module;`) : p3.push(`${e6.local}${i3}=${i3}module${o3(e6.imported)};`);
      if (d3) {
        const a4 = [];
        let l4 = false;
        for (const { imported: e6, reexported: t4 } of d3)
          "*" === t4 ? l4 = true : a4.push([t4, "*" === e6 ? "module" : `module${o3(e6)}`]);
        if (a4.length > 1 || l4) {
          const o4 = r3(a4, { lineBreakIndent: null });
          l4 ? (h3 || (h3 = ra({ dependencies: e5, exports: t3 })), p3.push(`${n3} setter${i3}=${i3}${o4};`, `for${i3}(${n3} name in module)${i3}{`, `${s3}if${i3}(!_starExcludes[name])${i3}setter[name]${i3}=${i3}module[name];`, "}", "exports(setter);")) : p3.push(`exports(${o4});`);
        } else {
          const [e6, t4] = a4[0];
          p3.push(`exports('${e6}',${i3}${t4});`);
        }
      }
      c3.push(p3.join(`${a3}${s3}${s3}${s3}`));
    }
    return { importBindings: l3, setters: c3, starExcludes: h3 };
  }(s2, i2, r2, a2), k2 = d2 ? `'${d2}',${g2}` : "", I2 = t2.has("module") ? ["exports", "module"] : n2 ? ["exports"] : [];
  let w2 = `System.register(${k2}[` + s2.map(({ importPath: e5 }) => `'${e5}'`).join(`,${g2}`) + `],${g2}(${x2(I2, { isAsync: false, name: null })}{${E2}${r2}${f2 ? "'use strict';" : ""}` + oa(A2, r2, a2) + aa(v2, r2, a2) + `${E2}${r2}return${g2}{${S2.length > 0 ? `${E2}${r2}${r2}setters:${g2}[${S2.map((e5) => e5 ? `${y2(["module"], { isAsync: false, name: null })}{${E2}${r2}${r2}${r2}${e5}${E2}${r2}${r2}}` : m2 ? "null" : `${y2([], { isAsync: false, name: null })}{}`).join(`,${g2}`)}],` : ""}${E2}`;
  w2 += `${r2}${r2}execute:${g2}(${x2([], { isAsync: c2, name: null })}{${E2}${E2}`;
  const P2 = `${r2}${r2}})${E2}${r2}}${b2}${E2}}));`;
  e4.prepend(o2 + Ir(null, t2, r2, a2, h2, u2, p2) + la(i2, r2, a2)).append(`${l2}${E2}${E2}` + ha(i2, r2, a2) + ua(i2, r2, a2)).indent(`${r2}${r2}${r2}`).append(P2).prepend(w2);
}, umd: function(e4, { accessedGlobals: t2, dependencies: s2, exports: i2, hasDefaultExport: n2, hasExports: r2, id: o2, indent: a2, intro: l2, namedExportsMode: c2, log: h2, outro: u2, snippets: d2 }, { amd: p2, compact: f2, esModule: m2, extend: g2, externalLiveBindings: y2, freeze: x2, interop: E2, name: b2, namespaceToStringTag: v2, globals: S2, noConflict: A2, strict: k2 }) {
  const { _: I2, cnst: w2, getFunctionIntro: P2, getNonArrowFunctionIntro: C2, getPropertyAccess: $2, n: N2, s: _2 } = d2, R2 = f2 ? "f" : "factory", M2 = f2 ? "g" : "global";
  if (r2 && !b2)
    return Qe({ code: bt, message: 'You must supply "output.name" for UMD bundles that have exports so that the exports are accessible in environments without a module loader.', url: De(Ke) });
  ta(h2, s2);
  const O2 = s2.map((e5) => `'${Jo(e5.importPath, p2.forceJsExtensionForImports)}'`), D2 = s2.map((e5) => `require('${e5.importPath}')`), L2 = na(s2), T2 = L2.map((e5) => da(e5.globalName, M2, $2)), V2 = L2.map((e5) => e5.name);
  c2 && (r2 || A2) && (O2.unshift("'exports'"), D2.unshift("exports"), T2.unshift(ia(b2, M2, S2, (g2 ? `${da(b2, M2, $2)}${I2}||${I2}` : "") + "{}", d2)), V2.unshift("exports"));
  const B2 = qo(p2, o2), z2 = (B2 ? `'${B2}',${I2}` : "") + (O2.length > 0 ? `[${O2.join(`,${I2}`)}],${I2}` : ""), F2 = p2.define, j2 = !c2 && r2 ? `module.exports${I2}=${I2}` : "", U2 = k2 ? `${I2}'use strict';${N2}` : "";
  let G2;
  if (A2) {
    const e5 = f2 ? "e" : "exports";
    let t3;
    if (!c2 && r2)
      t3 = `${w2} ${e5}${I2}=${I2}${ia(b2, M2, S2, `${R2}(${T2.join(`,${I2}`)})`, d2)};`;
    else {
      t3 = `${w2} ${e5}${I2}=${I2}${T2.shift()};${N2}${a2}${a2}${R2}(${[e5, ...T2].join(`,${I2}`)});`;
    }
    G2 = `(${P2([], { isAsync: false, name: null })}{${N2}${a2}${a2}${w2} current${I2}=${I2}${function(e6, t4, { _: s3, getPropertyAccess: i3 }) {
      let n3 = t4;
      return e6.split(".").map((e7) => n3 += i3(e7)).join(`${s3}&&${s3}`);
    }(b2, M2, d2)};${N2}${a2}${a2}${t3}${N2}${a2}${a2}${e5}.noConflict${I2}=${I2}${P2([], { isAsync: false, name: null })}{${I2}${da(b2, M2, $2)}${I2}=${I2}current;${I2}return ${e5}${_2}${I2}};${N2}${a2}})()`;
  } else
    G2 = `${R2}(${T2.join(`,${I2}`)})`, !c2 && r2 && (G2 = ia(b2, M2, S2, G2, d2));
  const W2 = r2 || A2 && c2 || T2.length > 0, q2 = [R2];
  W2 && q2.unshift(M2);
  const H2 = W2 ? `this,${I2}` : "", K2 = W2 ? `(${M2}${I2}=${I2}typeof globalThis${I2}!==${I2}'undefined'${I2}?${I2}globalThis${I2}:${I2}${M2}${I2}||${I2}self,${I2}` : "", Y2 = W2 ? ")" : "", X2 = W2 ? `${a2}typeof exports${I2}===${I2}'object'${I2}&&${I2}typeof module${I2}!==${I2}'undefined'${I2}?${I2}${j2}${R2}(${D2.join(`,${I2}`)})${I2}:${N2}` : "", Q2 = `(${C2(q2, { isAsync: false, name: null })}{${N2}` + X2 + `${a2}typeof ${F2}${I2}===${I2}'function'${I2}&&${I2}${F2}.amd${I2}?${I2}${F2}(${z2}${R2})${I2}:${N2}${a2}${K2}${G2}${Y2};${N2}})(${H2}(${C2(V2, { isAsync: false, name: null })}{${U2}${N2}`, Z2 = N2 + N2 + "}));";
  e4.prepend(`${l2}${Zo(s2, E2, y2, x2, v2, t2, a2, d2)}`);
  const J2 = Ho(i2, s2, c2, E2, d2, a2, y2);
  let ee2 = Xo(c2 && r2, true === m2 || "if-default-prop" === m2 && n2, v2, d2);
  ee2 && (ee2 = N2 + N2 + ee2), e4.append(`${J2}${ee2}${u2}`).trim().indent(a2).append(Z2).prepend(Q2);
} };
const fa = (e4, t2) => t2 ? `${e4}
${t2}` : e4, ma = (e4, t2) => t2 ? `${e4}

${t2}` : e4;
async function ga(e4, t2, s2) {
  try {
    let [i3, n3, r3, o2] = await Promise.all([t2.hookReduceValue("banner", e4.banner(s2), [s2], fa), t2.hookReduceValue("footer", e4.footer(s2), [s2], fa), t2.hookReduceValue("intro", e4.intro(s2), [s2], ma), t2.hookReduceValue("outro", e4.outro(s2), [s2], ma)]);
    return r3 && (r3 += "\n\n"), o2 && (o2 = `

${o2}`), i3 && (i3 += "\n"), n3 && (n3 = "\n" + n3), { banner: i3, footer: n3, intro: r3, outro: o2 };
  } catch (e5) {
    return Qe((i2 = e5.message, n2 = e5.hook, r2 = e5.plugin, { code: Je, message: `Could not retrieve "${n2}". Check configuration of plugin "${r2}".
	Error Message: ${i2}` }));
  }
  var i2, n2, r2;
}
const ya = { amd: ba, cjs: ba, es: Ea, iife: ba, system: Ea, umd: ba };
function xa(e4, t2, s2, i2, n2, r2, o2, a2, l2, c2, h2, u2, d2, p2) {
  const f2 = [...e4].reverse();
  for (const e5 of f2)
    e5.scope.addUsedOutsideNames(i2, n2, u2, d2);
  !function(e5, t3, s3) {
    for (const i3 of t3) {
      for (const t4 of i3.scope.variables.values())
        t4.included && !(t4.renderBaseName || t4 instanceof ho && t4.getOriginalVariable() !== t4) && t4.setRenderNames(null, Li(t4.name, e5, t4.forbiddenNames));
      if (s3.has(i3)) {
        const t4 = i3.namespace;
        t4.setRenderNames(null, Li(t4.name, e5, t4.forbiddenNames));
      }
    }
  }(i2, f2, p2), ya[n2](i2, s2, t2, r2, o2, a2, l2, c2, h2);
  for (const e5 of f2)
    e5.scope.deconflict(n2, u2, d2);
}
function Ea(e4, t2, s2, i2, n2, r2, o2, a2, l2) {
  for (const t3 of s2.dependencies)
    (n2 || t3 instanceof j) && (t3.variableName = Li(t3.suggestedVariableName, e4, null));
  for (const s3 of t2) {
    const t3 = s3.module, i3 = s3.name;
    s3.isNamespace && (n2 || t3 instanceof Jt) ? s3.setRenderNames(null, (t3 instanceof Jt ? a2.get(t3) : o2.get(t3)).variableName) : t3 instanceof Jt && "default" === i3 ? s3.setRenderNames(null, Li([...t3.exportedVariables].some(([e5, t4]) => "*" === t4 && e5.included) ? t3.suggestedVariableName + "__default" : t3.suggestedVariableName, e4, s3.forbiddenNames)) : s3.setRenderNames(null, Li(i3, e4, s3.forbiddenNames));
  }
  for (const t3 of l2)
    t3.setRenderNames(null, Li(t3.name, e4, t3.forbiddenNames));
}
function ba(e4, t2, { deconflictedDefault: s2, deconflictedNamespace: i2, dependencies: n2 }, r2, o2, a2, l2, c2) {
  for (const t3 of n2)
    t3.variableName = Li(t3.suggestedVariableName, e4, null);
  for (const t3 of i2)
    t3.namespaceVariableName = Li(`${t3.suggestedVariableName}__namespace`, e4, null);
  for (const t3 of s2)
    t3.defaultVariableName = i2.has(t3) && kr(r2(t3.id), a2) ? t3.namespaceVariableName : Li(`${t3.suggestedVariableName}__default`, e4, null);
  for (const e5 of t2) {
    const t3 = e5.module;
    if (t3 instanceof Jt) {
      const s3 = c2.get(t3), i3 = e5.name;
      if ("default" === i3) {
        const i4 = r2(t3.id), n3 = vr[i4] ? s3.defaultVariableName : s3.variableName;
        Sr(i4, a2) ? e5.setRenderNames(n3, "default") : e5.setRenderNames(null, n3);
      } else
        "*" === i3 ? e5.setRenderNames(null, Ar[r2(t3.id)] ? s3.namespaceVariableName : s3.variableName) : e5.setRenderNames(s3.variableName, null);
    } else {
      const s3 = l2.get(t3);
      o2 && e5.isNamespace ? e5.setRenderNames(null, "default" === s3.exportMode ? s3.namespaceVariableName : s3.variableName) : "default" === s3.exportMode ? e5.setRenderNames(null, s3.variableName) : e5.setRenderNames(s3.variableName, s3.getVariableExportName(e5));
    }
  }
}
function va(e4, { exports: t2, name: s2, format: i2 }, n2, r2) {
  const o2 = e4.getExportNames();
  if ("default" === t2) {
    if (1 !== o2.length || "default" !== o2[0])
      return Qe(Ft("default", o2, n2));
  } else if ("none" === t2 && o2.length > 0)
    return Qe(Ft("none", o2, n2));
  return "auto" === t2 && (0 === o2.length ? t2 = "none" : 1 === o2.length && "default" === o2[0] ? t2 = "default" : ("es" !== i2 && "system" !== i2 && o2.includes("default") && r2(Ae, function(e5, t3) {
    return { code: At, id: e5, message: `Entry module "${V(e5)}" is using named and default exports together. Consumers of your bundle will have to use \`${t3 || "chunk"}.default\` to access the default export, which may not be what you want. Use \`output.exports: "named"\` to disable this warning.`, url: De(ze) };
  }(n2, s2)), t2 = "named")), t2;
}
function Sa(e4) {
  const t2 = e4.split("\n"), s2 = t2.filter((e5) => /^\t+/.test(e5)), i2 = t2.filter((e5) => /^ {2,}/.test(e5));
  if (0 === s2.length && 0 === i2.length)
    return null;
  if (s2.length >= i2.length)
    return "	";
  const n2 = i2.reduce((e5, t3) => {
    const s3 = /^ +/.exec(t3)[0].length;
    return Math.min(s3, e5);
  }, 1 / 0);
  return " ".repeat(n2);
}
function Aa(e4, t2, s2, i2, n2, r2) {
  const o2 = e4.getDependenciesToBeIncluded();
  for (const e5 of o2) {
    if (e5 instanceof Jt) {
      t2.push(r2.get(e5));
      continue;
    }
    const o3 = n2.get(e5);
    o3 === i2 ? s2.has(e5) || (s2.add(e5), Aa(e5, t2, s2, i2, n2, r2)) : t2.push(o3);
  }
}
const ka = "!~{", Ia = "}~", wa = new RegExp(`${ka}[0-9a-zA-Z_$]{1,59}${Ia}`, "g"), Pa = (e4, t2) => e4.replace(wa, (e5) => t2.get(e5) || e5), Ca = (e4, t2, s2) => e4.replace(wa, (e5) => e5 === t2 ? s2 : e5), $a = (e4, t2) => {
  const s2 = /* @__PURE__ */ new Set(), i2 = e4.replace(wa, (e5) => t2.has(e5) ? (s2.add(e5), `${ka}${"0".repeat(e5.length - 5)}${Ia}`) : e5);
  return { containedPlaceholders: s2, transformedCode: i2 };
}, Na = Symbol("bundleKeys"), _a = { type: "placeholder" };
function Ra(e4, t2, s2) {
  return B(e4) ? Qe(Xt(`Invalid pattern "${e4}" for "${t2}", patterns can be neither absolute nor relative paths. If you want your files to be stored in a subdirectory, write its name without a leading slash like this: subdirectory/pattern.`)) : e4.replace(/\[(\w+)(:\d+)?]/g, (e5, i2, n2) => {
    if (!s2.hasOwnProperty(i2) || n2 && "hash" !== i2)
      return Qe(Xt(`"[${i2}${n2 || ""}]" is not a valid placeholder in the "${t2}" pattern.`));
    const r2 = s2[i2](n2 && Number.parseInt(n2.slice(1)));
    return B(r2) ? Qe(Xt(`Invalid substitution "${r2}" for placeholder "[${i2}]" in "${t2}" pattern, can be neither absolute nor relative path.`)) : r2;
  });
}
function Ma(e4, { [Na]: t2 }) {
  if (!t2.has(e4.toLowerCase()))
    return e4;
  const s2 = N(e4);
  e4 = e4.slice(0, Math.max(0, e4.length - s2.length));
  let i2, n2 = 1;
  for (; t2.has((i2 = e4 + ++n2 + s2).toLowerCase()); )
    ;
  return i2;
}
const Oa = /* @__PURE__ */ new Set([".js", ".jsx", ".ts", ".tsx", ".mjs", ".mts", ".cjs", ".cts"]);
function Da(e4, t2, s2, i2) {
  const n2 = "function" == typeof t2 ? t2(e4.id) : t2[e4.id];
  return n2 || (s2 ? (i2(Ae, (r2 = e4.id, o2 = e4.variableName, { code: xt, id: r2, message: `No name was provided for external module "${r2}" in "output.globals" – guessing "${o2}".`, names: [o2], url: De(Ge) })), e4.variableName) : void 0);
  var r2, o2;
}
class La {
  constructor(e4, t2, s2, i2, n2, r2, o2, a2, l2, c2, h2, u2, d2, p2, f2) {
    this.orderedModules = e4, this.inputOptions = t2, this.outputOptions = s2, this.unsetOptions = i2, this.pluginDriver = n2, this.modulesById = r2, this.chunkByModule = o2, this.externalChunkByModule = a2, this.facadeChunkByModule = l2, this.includedNamespaces = c2, this.manualChunkAlias = h2, this.getPlaceholder = u2, this.bundle = d2, this.inputBase = p2, this.snippets = f2, this.entryModules = [], this.exportMode = "named", this.facadeModule = null, this.namespaceVariableName = "", this.variableName = "", this.accessedGlobalsByScope = /* @__PURE__ */ new Map(), this.dependencies = /* @__PURE__ */ new Set(), this.dynamicEntryModules = [], this.dynamicName = null, this.exportNamesByVariable = /* @__PURE__ */ new Map(), this.exports = /* @__PURE__ */ new Set(), this.exportsByName = /* @__PURE__ */ new Map(), this.fileName = null, this.implicitEntryModules = [], this.implicitlyLoadedBefore = /* @__PURE__ */ new Set(), this.imports = /* @__PURE__ */ new Set(), this.includedDynamicImports = null, this.includedReexportsByModule = /* @__PURE__ */ new Map(), this.isEmpty = true, this.name = null, this.needsExportsShim = false, this.preRenderedChunkInfo = null, this.preliminaryFileName = null, this.preliminarySourcemapFileName = null, this.renderedChunkInfo = null, this.renderedDependencies = null, this.renderedModules = /* @__PURE__ */ Object.create(null), this.sortedExportNames = null, this.strictFacade = false, this.execIndex = e4.length > 0 ? e4[0].execIndex : 1 / 0;
    const m2 = new Set(e4);
    for (const t3 of e4) {
      o2.set(t3, this), t3.namespace.included && !s2.preserveModules && c2.add(t3), this.isEmpty && t3.isIncluded() && (this.isEmpty = false), (t3.info.isEntry || s2.preserveModules) && this.entryModules.push(t3);
      for (const e5 of t3.includedDynamicImporters)
        m2.has(e5) || (this.dynamicEntryModules.push(t3), t3.info.syntheticNamedExports && (c2.add(t3), this.exports.add(t3.namespace)));
      t3.implicitlyLoadedAfter.size > 0 && this.implicitEntryModules.push(t3);
    }
    this.suggestedVariableName = Se(this.generateVariableName());
  }
  static generateFacade(e4, t2, s2, i2, n2, r2, o2, a2, l2, c2, h2, u2, d2, p2, f2) {
    const m2 = new La([], e4, t2, s2, i2, n2, r2, o2, a2, l2, null, u2, d2, p2, f2);
    m2.assignFacadeName(h2, c2), a2.has(c2) || a2.set(c2, m2);
    for (const e5 of c2.getDependenciesToBeIncluded())
      m2.dependencies.add(e5 instanceof jo ? r2.get(e5) : o2.get(e5));
    return !m2.dependencies.has(r2.get(c2)) && c2.info.moduleSideEffects && c2.hasEffects() && m2.dependencies.add(r2.get(c2)), m2.ensureReexportsAreAvailableForModule(c2), m2.facadeModule = c2, m2.strictFacade = true, m2;
  }
  canModuleBeFacade(e4, t2) {
    const s2 = e4.getExportNamesByVariable();
    for (const e5 of this.exports)
      if (!s2.has(e5))
        return false;
    for (const i2 of t2)
      if (!(i2.module === e4 || s2.has(i2) || i2 instanceof Eo && s2.has(i2.getBaseVariable())))
        return false;
    return true;
  }
  finalizeChunk(e4, t2, s2, i2) {
    const n2 = this.getRenderedChunkInfo(), r2 = (e5) => Pa(e5, i2), o2 = n2.fileName, a2 = this.fileName = r2(o2);
    return { ...n2, code: e4, dynamicImports: n2.dynamicImports.map(r2), fileName: a2, implicitlyLoadedBefore: n2.implicitlyLoadedBefore.map(r2), importedBindings: Object.fromEntries(Object.entries(n2.importedBindings).map(([e5, t3]) => [r2(e5), t3])), imports: n2.imports.map(r2), map: t2, preliminaryFileName: o2, referencedFiles: n2.referencedFiles.map(r2), sourcemapFileName: s2 };
  }
  generateExports() {
    this.sortedExportNames = null;
    const e4 = new Set(this.exports);
    if (null !== this.facadeModule && (false !== this.facadeModule.preserveSignature || this.strictFacade)) {
      const t2 = this.facadeModule.getExportNamesByVariable();
      for (const [s2, i2] of t2) {
        this.exportNamesByVariable.set(s2, [...i2]);
        for (const e5 of i2)
          this.exportsByName.set(e5, s2);
        e4.delete(s2);
      }
    }
    this.outputOptions.minifyInternalExports ? function(e5, t2, s2) {
      let i2 = 0;
      for (const n2 of e5) {
        let [e6] = n2.name;
        if (t2.has(e6))
          do {
            e6 = Di(++i2), 49 === e6.charCodeAt(0) && (i2 += 9 * 64 ** (e6.length - 1), e6 = Di(i2));
          } while (Ee.has(e6) || t2.has(e6));
        t2.set(e6, n2), s2.set(n2, [e6]);
      }
    }(e4, this.exportsByName, this.exportNamesByVariable) : function(e5, t2, s2) {
      for (const i2 of e5) {
        let e6 = 0, n2 = i2.name;
        for (; t2.has(n2); )
          n2 = i2.name + "$" + ++e6;
        t2.set(n2, i2), s2.set(i2, [n2]);
      }
    }(e4, this.exportsByName, this.exportNamesByVariable), (this.outputOptions.preserveModules || this.facadeModule && this.facadeModule.info.isEntry) && (this.exportMode = va(this, this.outputOptions, this.facadeModule.id, this.inputOptions.onLog));
  }
  generateFacades() {
    var _a2;
    const e4 = [], t2 = /* @__PURE__ */ new Set([...this.entryModules, ...this.implicitEntryModules]), s2 = new Set(this.dynamicEntryModules.map(({ namespace: e5 }) => e5));
    for (const e5 of t2)
      if (e5.preserveSignature)
        for (const t3 of e5.getExportNamesByVariable().keys())
          this.chunkByModule.get(t3.module) === this && s2.add(t3);
    for (const i2 of t2) {
      const t3 = Array.from(new Set(i2.chunkNames.filter(({ isUserDefined: e5 }) => e5).map(({ name: e5 }) => e5)), (e5) => ({ name: e5 }));
      if (0 === t3.length && i2.isUserDefinedEntryPoint && t3.push({}), t3.push(...Array.from(i2.chunkFileNames, (e5) => ({ fileName: e5 }))), 0 === t3.length && t3.push({}), !this.facadeModule) {
        const e5 = !this.outputOptions.preserveModules && ("strict" === i2.preserveSignature || "exports-only" === i2.preserveSignature && i2.getExportNamesByVariable().size > 0);
        e5 && !this.canModuleBeFacade(i2, s2) || (this.facadeModule = i2, this.facadeChunkByModule.set(i2, this), i2.preserveSignature && (this.strictFacade = e5), this.assignFacadeName(t3.shift(), i2, this.outputOptions.preserveModules));
      }
      for (const s3 of t3)
        e4.push(La.generateFacade(this.inputOptions, this.outputOptions, this.unsetOptions, this.pluginDriver, this.modulesById, this.chunkByModule, this.externalChunkByModule, this.facadeChunkByModule, this.includedNamespaces, i2, s3, this.getPlaceholder, this.bundle, this.inputBase, this.snippets));
    }
    for (const e5 of this.dynamicEntryModules)
      e5.info.syntheticNamedExports || (!this.facadeModule && this.canModuleBeFacade(e5, s2) ? (this.facadeModule = e5, this.facadeChunkByModule.set(e5, this), this.strictFacade = true, this.dynamicName = Ta(e5)) : this.facadeModule === e5 && !this.strictFacade && this.canModuleBeFacade(e5, s2) ? this.strictFacade = true : ((_a2 = this.facadeChunkByModule.get(e5)) == null ? void 0 : _a2.strictFacade) || (this.includedNamespaces.add(e5), this.exports.add(e5.namespace)));
    return this.outputOptions.preserveModules || this.addNecessaryImportsForFacades(), e4;
  }
  getChunkName() {
    return this.name ?? (this.name = this.outputOptions.sanitizeFileName(this.getFallbackChunkName()));
  }
  getExportNames() {
    return this.sortedExportNames ?? (this.sortedExportNames = [...this.exportsByName.keys()].sort());
  }
  getFileName() {
    return this.fileName || this.getPreliminaryFileName().fileName;
  }
  getImportPath(e4) {
    return L(F(e4, this.getFileName(), "amd" === this.outputOptions.format && !this.outputOptions.amd.forceJsExtensionForImports, true));
  }
  getPreliminaryFileName() {
    var _a2;
    if (this.preliminaryFileName)
      return this.preliminaryFileName;
    let e4, t2 = null;
    const { chunkFileNames: s2, entryFileNames: i2, file: n2, format: r2, preserveModules: o2 } = this.outputOptions;
    if (n2)
      e4 = C(n2);
    else if (null === this.fileName) {
      const [n3, a2] = o2 || ((_a2 = this.facadeModule) == null ? void 0 : _a2.isUserDefinedEntryPoint) ? [i2, "output.entryFileNames"] : [s2, "output.chunkFileNames"];
      e4 = Ra("function" == typeof n3 ? n3(this.getPreRenderedChunkInfo()) : n3, a2, { format: () => r2, hash: (e5) => t2 || (t2 = this.getPlaceholder(a2, e5)), name: () => this.getChunkName() }), t2 || (e4 = Ma(e4, this.bundle));
    } else
      e4 = this.fileName;
    return t2 || (this.bundle[e4] = _a), this.preliminaryFileName = { fileName: e4, hashPlaceholder: t2 };
  }
  getPreliminarySourcemapFileName() {
    if (this.preliminarySourcemapFileName)
      return this.preliminarySourcemapFileName;
    let e4 = null, t2 = null;
    const { sourcemapFileNames: s2, format: i2 } = this.outputOptions;
    if (!s2)
      return null;
    {
      const [n2, r2] = [s2, "output.sourcemapFileNames"];
      e4 = Ra("function" == typeof n2 ? n2(this.getPreRenderedChunkInfo()) : n2, r2, { chunkhash: () => this.getPreliminaryFileName().hashPlaceholder || "", format: () => i2, hash: (e5) => t2 || (t2 = this.getPlaceholder(r2, e5)), name: () => this.getChunkName() }), t2 || (e4 = Ma(e4, this.bundle));
    }
    return this.preliminarySourcemapFileName = { fileName: e4, hashPlaceholder: t2 };
  }
  getRenderedChunkInfo() {
    return this.renderedChunkInfo ? this.renderedChunkInfo : this.renderedChunkInfo = { ...this.getPreRenderedChunkInfo(), dynamicImports: this.getDynamicDependencies().map(Fa), fileName: this.getFileName(), implicitlyLoadedBefore: Array.from(this.implicitlyLoadedBefore, Fa), importedBindings: Ba(this.getRenderedDependencies(), Fa), imports: Array.from(this.dependencies, Fa), modules: this.renderedModules, referencedFiles: this.getReferencedFiles() };
  }
  getVariableExportName(e4) {
    return this.outputOptions.preserveModules && e4 instanceof xo ? "*" : this.exportNamesByVariable.get(e4)[0];
  }
  link() {
    this.dependencies = function(e4, t2, s2, i2) {
      const n2 = [], r2 = /* @__PURE__ */ new Set();
      for (let o3 = t2.length - 1; o3 >= 0; o3--) {
        const a2 = t2[o3];
        if (!r2.has(a2)) {
          const t3 = [];
          Aa(a2, t3, r2, e4, s2, i2), n2.unshift(t3);
        }
      }
      const o2 = /* @__PURE__ */ new Set();
      for (const e5 of n2)
        for (const t3 of e5)
          o2.add(t3);
      return o2;
    }(this, this.orderedModules, this.chunkByModule, this.externalChunkByModule);
    for (const e4 of this.orderedModules)
      this.addImplicitlyLoadedBeforeFromModule(e4), this.setUpChunkImportsAndExportsForModule(e4);
  }
  async render() {
    const { dependencies: e4, exportMode: t2, facadeModule: s2, inputOptions: { onLog: i2 }, outputOptions: n2, pluginDriver: r2, snippets: o2 } = this, { format: a2, hoistTransitiveImports: l2, preserveModules: c2 } = n2;
    if (l2 && !c2 && null !== s2)
      for (const t3 of e4)
        t3 instanceof La && this.inlineChunkDependencies(t3);
    const h2 = this.getPreliminaryFileName(), u2 = this.getPreliminarySourcemapFileName(), { accessedGlobals: d2, indent: p2, magicString: f2, renderedSource: m2, usedModules: g2, usesTopLevelAwait: y2 } = this.renderModules(h2.fileName), x2 = [...this.getRenderedDependencies().values()], E2 = "none" === t2 ? [] : this.getChunkExportDeclarations(a2);
    let b2 = E2.length > 0, v2 = false;
    for (const e5 of x2) {
      const { reexports: t3 } = e5;
      (t3 == null ? void 0 : t3.length) && (b2 = true, !v2 && t3.some((e6) => "default" === e6.reexported) && (v2 = true), "es" === a2 && (e5.reexports = t3.filter(({ reexported: e6 }) => !E2.find(({ exported: t4 }) => t4 === e6))));
    }
    if (!v2) {
      for (const { exported: e5 } of E2)
        if ("default" === e5) {
          v2 = true;
          break;
        }
    }
    const { intro: S2, outro: A2, banner: k2, footer: I2 } = await ga(n2, r2, this.getRenderedChunkInfo());
    return pa[a2](m2, { accessedGlobals: d2, dependencies: x2, exports: E2, hasDefaultExport: v2, hasExports: b2, id: h2.fileName, indent: p2, intro: S2, isEntryFacade: c2 || null !== s2 && s2.info.isEntry, isModuleFacade: null !== s2, log: i2, namedExportsMode: "default" !== t2, outro: A2, snippets: o2, usesTopLevelAwait: y2 }, n2), k2 && f2.prepend(k2), I2 && f2.append(I2), { chunk: this, magicString: f2, preliminaryFileName: h2, preliminarySourcemapFileName: u2, usedModules: g2 };
  }
  addImplicitlyLoadedBeforeFromModule(e4) {
    const { chunkByModule: t2, implicitlyLoadedBefore: s2 } = this;
    for (const i2 of e4.implicitlyLoadedBefore) {
      const e5 = t2.get(i2);
      e5 && e5 !== this && s2.add(e5);
    }
  }
  addNecessaryImportsForFacades() {
    for (const [e4, t2] of this.includedReexportsByModule)
      if (this.includedNamespaces.has(e4))
        for (const e5 of t2)
          this.imports.add(e5);
  }
  assignFacadeName({ fileName: e4, name: t2 }, s2, i2) {
    e4 ? this.fileName = e4 : this.name = this.outputOptions.sanitizeFileName(t2 || (i2 ? this.getPreserveModulesChunkNameFromModule(s2) : Ta(s2)));
  }
  checkCircularDependencyImport(e4, t2) {
    var _a2;
    const s2 = e4.module;
    if (s2 instanceof jo) {
      const l2 = this.chunkByModule.get(s2);
      let c2;
      do {
        if (c2 = t2.alternativeReexportModules.get(e4), c2) {
          this.chunkByModule.get(c2) !== l2 && this.inputOptions.onLog(Ae, (i2 = ((_a2 = s2.getExportNamesByVariable().get(e4)) == null ? void 0 : _a2[0]) || "*", n2 = s2.id, r2 = c2.id, o2 = t2.id, a2 = this.outputOptions.preserveModules, { code: "CYCLIC_CROSS_CHUNK_REEXPORT", exporter: n2, id: o2, message: `Export "${i2}" of module "${V(n2)}" was reexported through module "${V(r2)}" while both modules are dependencies of each other and will end up in different chunks by current Rollup settings. This scenario is not well supported at the moment as it will produce a circular dependency between chunks and will likely lead to broken execution order.
Either change the import in "${V(o2)}" to point directly to the exporting module or ${a2 ? 'do not use "output.preserveModules"' : 'reconfigure "output.manualChunks"'} to ensure these modules end up in the same chunk.`, reexporter: r2 })), t2 = c2;
        }
      } while (c2);
    }
    var i2, n2, r2, o2, a2;
  }
  ensureReexportsAreAvailableForModule(e4) {
    const t2 = [], s2 = e4.getExportNamesByVariable();
    for (const i2 of s2.keys()) {
      const s3 = i2 instanceof Eo, n2 = s3 ? i2.getBaseVariable() : i2;
      if (this.checkCircularDependencyImport(n2, e4), !(n2 instanceof xo && this.outputOptions.preserveModules)) {
        const e5 = n2.module;
        if (e5 instanceof jo) {
          const i3 = this.chunkByModule.get(e5);
          i3 && i3 !== this && (i3.exports.add(n2), t2.push(n2), s3 && this.imports.add(n2));
        }
      }
    }
    t2.length > 0 && this.includedReexportsByModule.set(e4, t2);
  }
  generateVariableName() {
    if (this.manualChunkAlias)
      return this.manualChunkAlias;
    const e4 = this.entryModules[0] || this.implicitEntryModules[0] || this.dynamicEntryModules[0] || this.orderedModules[this.orderedModules.length - 1];
    return e4 ? Ta(e4) : "chunk";
  }
  getChunkExportDeclarations(e4) {
    const t2 = [];
    for (const s2 of this.getExportNames()) {
      if ("*" === s2[0])
        continue;
      const i2 = this.exportsByName.get(s2);
      if (!(i2 instanceof Eo)) {
        const t3 = i2.module;
        if (t3) {
          const i3 = this.chunkByModule.get(t3);
          if (i3 !== this) {
            if (!i3 || "es" !== e4)
              continue;
            const t4 = this.renderedDependencies.get(i3);
            if (!t4)
              continue;
            const { imports: n3, reexports: r3 } = t4, o3 = r3 == null ? void 0 : r3.find(({ reexported: e5 }) => e5 === s2), a2 = n3 == null ? void 0 : n3.find(({ imported: e5 }) => e5 === (o3 == null ? void 0 : o3.imported));
            if (!a2)
              continue;
          }
        }
      }
      let n2 = null, r2 = false, o2 = i2.getName(this.snippets.getPropertyAccess);
      if (i2 instanceof Pi) {
        for (const e5 of i2.declarations)
          if (e5.parent instanceof nr || e5 instanceof rr && e5.declaration instanceof nr) {
            r2 = true;
            break;
          }
      } else
        i2 instanceof Eo && (n2 = o2, "es" === e4 && (o2 = i2.renderName));
      t2.push({ exported: s2, expression: n2, hoisted: r2, local: o2 });
    }
    return t2;
  }
  getDependenciesToBeDeconflicted(e4, t2, s2) {
    const i2 = /* @__PURE__ */ new Set(), n2 = /* @__PURE__ */ new Set(), r2 = /* @__PURE__ */ new Set();
    for (const t3 of [...this.exportNamesByVariable.keys(), ...this.imports])
      if (e4 || t3.isNamespace) {
        const o2 = t3.module;
        if (o2 instanceof Jt) {
          const a2 = this.externalChunkByModule.get(o2);
          i2.add(a2), e4 && ("default" === t3.name ? vr[s2(o2.id)] && n2.add(a2) : "*" === t3.name && Ar[s2(o2.id)] && r2.add(a2));
        } else {
          const s3 = this.chunkByModule.get(o2);
          s3 !== this && (i2.add(s3), e4 && "default" === s3.exportMode && t3.isNamespace && r2.add(s3));
        }
      }
    if (t2)
      for (const e5 of this.dependencies)
        i2.add(e5);
    return { deconflictedDefault: n2, deconflictedNamespace: r2, dependencies: i2 };
  }
  getDynamicDependencies() {
    return this.getIncludedDynamicImports().map((e4) => e4.facadeChunk || e4.chunk || e4.externalChunk || e4.resolution).filter((e4) => e4 !== this && (e4 instanceof La || e4 instanceof j));
  }
  getDynamicImportStringAndAssertions(e4, t2) {
    if (e4 instanceof Jt) {
      const s2 = this.externalChunkByModule.get(e4);
      return [`'${s2.getImportPath(t2)}'`, s2.getImportAssertions(this.snippets)];
    }
    return [e4 || "", "es" === this.outputOptions.format && this.outputOptions.externalImportAssertions || null];
  }
  getFallbackChunkName() {
    return this.manualChunkAlias ? this.manualChunkAlias : this.dynamicName ? this.dynamicName : this.fileName ? T(this.fileName) : T(this.orderedModules[this.orderedModules.length - 1].id);
  }
  getImportSpecifiers() {
    const { interop: e4 } = this.outputOptions, t2 = /* @__PURE__ */ new Map();
    for (const s2 of this.imports) {
      const i2 = s2.module;
      let n2, r2;
      if (i2 instanceof Jt) {
        if (n2 = this.externalChunkByModule.get(i2), r2 = s2.name, "default" !== r2 && "*" !== r2 && "defaultOnly" === e4(i2.id))
          return Qe(Kt(i2.id, r2, false));
      } else
        n2 = this.chunkByModule.get(i2), r2 = n2.getVariableExportName(s2);
      U(t2, n2, W).push({ imported: r2, local: s2.getName(this.snippets.getPropertyAccess) });
    }
    return t2;
  }
  getIncludedDynamicImports() {
    if (this.includedDynamicImports)
      return this.includedDynamicImports;
    const e4 = [];
    for (const t2 of this.orderedModules)
      for (const { node: s2, resolution: i2 } of t2.dynamicImports)
        s2.included && e4.push(i2 instanceof jo ? { chunk: this.chunkByModule.get(i2), externalChunk: null, facadeChunk: this.facadeChunkByModule.get(i2), node: s2, resolution: i2 } : i2 instanceof Jt ? { chunk: null, externalChunk: this.externalChunkByModule.get(i2), facadeChunk: null, node: s2, resolution: i2 } : { chunk: null, externalChunk: null, facadeChunk: null, node: s2, resolution: i2 });
    return this.includedDynamicImports = e4;
  }
  getPreRenderedChunkInfo() {
    if (this.preRenderedChunkInfo)
      return this.preRenderedChunkInfo;
    const { dynamicEntryModules: e4, facadeModule: t2, implicitEntryModules: s2, orderedModules: i2 } = this;
    return this.preRenderedChunkInfo = { exports: this.getExportNames(), facadeModuleId: t2 && t2.id, isDynamicEntry: e4.length > 0, isEntry: !!(t2 == null ? void 0 : t2.info.isEntry), isImplicitEntry: s2.length > 0, moduleIds: i2.map(({ id: e5 }) => e5), name: this.getChunkName(), type: "chunk" };
  }
  getPreserveModulesChunkNameFromModule(e4) {
    const t2 = Va(e4);
    if (t2)
      return t2;
    const { preserveModulesRoot: s2, sanitizeFileName: i2 } = this.outputOptions, n2 = i2(P(e4.id.split(za, 1)[0])), r2 = N(n2), o2 = Oa.has(r2) ? n2.slice(0, -r2.length) : n2;
    return I(o2) ? s2 && R(o2).startsWith(s2) ? o2.slice(s2.length).replace(/^[/\\]/, "") : _(this.inputBase, o2) : `_virtual/${C(o2)}`;
  }
  getReexportSpecifiers() {
    const { externalLiveBindings: e4, interop: t2 } = this.outputOptions, s2 = /* @__PURE__ */ new Map();
    for (let i2 of this.getExportNames()) {
      let n2, r2, o2 = false;
      if ("*" === i2[0]) {
        const s3 = i2.slice(1);
        "defaultOnly" === t2(s3) && this.inputOptions.onLog(Ae, Yt(s3)), o2 = e4, n2 = this.externalChunkByModule.get(this.modulesById.get(s3)), r2 = i2 = "*";
      } else {
        const s3 = this.exportsByName.get(i2);
        if (s3 instanceof Eo)
          continue;
        const a2 = s3.module;
        if (a2 instanceof jo) {
          if (n2 = this.chunkByModule.get(a2), n2 === this)
            continue;
          r2 = n2.getVariableExportName(s3), o2 = s3.isReassigned;
        } else {
          if (n2 = this.externalChunkByModule.get(a2), r2 = s3.name, "default" !== r2 && "*" !== r2 && "defaultOnly" === t2(a2.id))
            return Qe(Kt(a2.id, r2, true));
          o2 = e4 && ("default" !== r2 || Sr(t2(a2.id), true));
        }
      }
      U(s2, n2, W).push({ imported: r2, needsLiveBinding: o2, reexported: i2 });
    }
    return s2;
  }
  getReferencedFiles() {
    const e4 = /* @__PURE__ */ new Set();
    for (const t2 of this.orderedModules)
      for (const s2 of t2.importMetas) {
        const t3 = s2.getReferencedFileName(this.pluginDriver);
        t3 && e4.add(t3);
      }
    return [...e4];
  }
  getRenderedDependencies() {
    if (this.renderedDependencies)
      return this.renderedDependencies;
    const e4 = this.getImportSpecifiers(), t2 = this.getReexportSpecifiers(), s2 = /* @__PURE__ */ new Map(), i2 = this.getFileName();
    for (const n2 of this.dependencies) {
      const r2 = e4.get(n2) || null, o2 = t2.get(n2) || null, a2 = n2 instanceof j || "default" !== n2.exportMode, l2 = n2.getImportPath(i2);
      s2.set(n2, { assertions: n2 instanceof j ? n2.getImportAssertions(this.snippets) : null, defaultVariableName: n2.defaultVariableName, globalName: n2 instanceof j && ("umd" === this.outputOptions.format || "iife" === this.outputOptions.format) && Da(n2, this.outputOptions.globals, null !== (r2 || o2), this.inputOptions.onLog), importPath: l2, imports: r2, isChunk: n2 instanceof La, name: n2.variableName, namedExportsMode: a2, namespaceVariableName: n2.namespaceVariableName, reexports: o2 });
    }
    return this.renderedDependencies = s2;
  }
  inlineChunkDependencies(e4) {
    for (const t2 of e4.dependencies)
      this.dependencies.has(t2) || (this.dependencies.add(t2), t2 instanceof La && this.inlineChunkDependencies(t2));
  }
  renderModules(e4) {
    var _a2;
    const { accessedGlobalsByScope: t2, dependencies: s2, exportNamesByVariable: i2, includedNamespaces: n2, inputOptions: { onLog: r2 }, isEmpty: o2, orderedModules: a2, outputOptions: l2, pluginDriver: c2, renderedModules: h2, snippets: u2 } = this, { compact: d2, dynamicImportFunction: p2, format: f2, freeze: m2, namespaceToStringTag: g2 } = l2, { _: x2, cnst: b2, n: v2 } = u2;
    this.setDynamicImportResolutions(e4), this.setImportMetaResolutions(e4), this.setIdentifierRenderResolutions();
    const S2 = new E({ separator: `${v2}${v2}` }), A2 = function(e5, t3) {
      if (true !== t3.indent)
        return t3.indent;
      for (const t4 of e5) {
        const e6 = Sa(t4.originalCode);
        if (null !== e6)
          return e6;
      }
      return "	";
    }(a2, l2), k2 = [];
    let I2 = "";
    const w2 = /* @__PURE__ */ new Set(), P2 = /* @__PURE__ */ new Map(), C2 = { accessedDocumentCurrentScript: false, dynamicImportFunction: p2, exportNamesByVariable: i2, format: f2, freeze: m2, indent: A2, namespaceToStringTag: g2, pluginDriver: c2, snippets: u2, useOriginalName: null };
    let $2 = false;
    for (const e5 of a2) {
      let s3, i3 = 0;
      if (e5.isIncluded() || n2.has(e5)) {
        const r4 = e5.render(C2);
        !C2.accessedDocumentCurrentScript && Yr.includes(f2) && ((_a2 = this.accessedGlobalsByScope.get(e5.scope)) == null ? void 0 : _a2.delete(br)), C2.accessedDocumentCurrentScript = false, { source: s3 } = r4, $2 || ($2 = r4.usesTopLevelAwait), i3 = s3.length(), i3 && (d2 && s3.lastLine().includes("//") && s3.append("\n"), P2.set(e5, s3), S2.addSource(s3), k2.push(e5));
        const o4 = e5.namespace;
        if (n2.has(e5)) {
          const e6 = o4.renderBlock(C2);
          o4.renderFirst() ? I2 += v2 + e6 : S2.addSource(new y(e6));
        }
        const a3 = t2.get(e5.scope);
        if (a3)
          for (const e6 of a3)
            w2.add(e6);
      }
      const { renderedExports: r3, removedExports: o3 } = e5.getRenderedExports();
      h2[e5.id] = { get code() {
        return (s3 == null ? void 0 : s3.toString()) ?? null;
      }, originalLength: e5.originalCode.length, removedExports: o3, renderedExports: r3, renderedLength: i3 };
    }
    I2 && S2.prepend(I2 + v2 + v2), this.needsExportsShim && S2.prepend(`${v2}${b2} ${go}${x2}=${x2}void 0;${v2}${v2}`);
    const N2 = d2 ? S2 : S2.trim();
    var _2;
    return o2 && 0 === this.getExportNames().length && 0 === s2.size && r2(Ae, { code: "EMPTY_BUNDLE", message: `Generated an empty chunk: "${_2 = this.getChunkName()}".`, names: [_2] }), { accessedGlobals: w2, indent: A2, magicString: S2, renderedSource: N2, usedModules: k2, usesTopLevelAwait: $2 };
  }
  setDynamicImportResolutions(e4) {
    const { accessedGlobalsByScope: t2, outputOptions: s2, pluginDriver: i2, snippets: n2 } = this;
    for (const r2 of this.getIncludedDynamicImports())
      if (r2.chunk) {
        const { chunk: o2, facadeChunk: a2, node: l2, resolution: c2 } = r2;
        o2 === this ? l2.setInternalResolution(c2.namespace) : l2.setExternalResolution((a2 || o2).exportMode, c2, s2, n2, i2, t2, `'${(a2 || o2).getImportPath(e4)}'`, !(a2 == null ? void 0 : a2.strictFacade) && o2.exportNamesByVariable.get(c2.namespace)[0], null);
      } else {
        const { node: o2, resolution: a2 } = r2, [l2, c2] = this.getDynamicImportStringAndAssertions(a2, e4);
        o2.setExternalResolution("external", a2, s2, n2, i2, t2, l2, false, c2);
      }
  }
  setIdentifierRenderResolutions() {
    const { format: e4, interop: t2, namespaceToStringTag: s2, preserveModules: i2, externalLiveBindings: n2 } = this.outputOptions, r2 = /* @__PURE__ */ new Set();
    for (const t3 of this.getExportNames()) {
      const s3 = this.exportsByName.get(t3);
      "es" !== e4 && "system" !== e4 && s3.isReassigned && !s3.isId ? s3.setRenderNames("exports", t3) : s3 instanceof Eo ? r2.add(s3) : s3.setRenderNames(null, null);
    }
    for (const e5 of this.orderedModules)
      if (e5.needsExportShim) {
        this.needsExportsShim = true;
        break;
      }
    const o2 = /* @__PURE__ */ new Set(["Object", "Promise"]);
    switch (this.needsExportsShim && o2.add(go), s2 && o2.add("Symbol"), e4) {
      case "system":
        o2.add("module").add("exports");
        break;
      case "es":
        break;
      case "cjs":
        o2.add("module").add("require").add("__filename").add("__dirname");
      default:
        o2.add("exports");
        for (const e5 of Br)
          o2.add(e5);
    }
    xa(this.orderedModules, this.getDependenciesToBeDeconflicted("es" !== e4 && "system" !== e4, "amd" === e4 || "umd" === e4 || "iife" === e4, t2), this.imports, o2, e4, t2, i2, n2, this.chunkByModule, this.externalChunkByModule, r2, this.exportNamesByVariable, this.accessedGlobalsByScope, this.includedNamespaces);
  }
  setImportMetaResolutions(e4) {
    const { accessedGlobalsByScope: t2, includedNamespaces: s2, orderedModules: i2, outputOptions: { format: n2 } } = this;
    for (const r2 of i2) {
      for (const s3 of r2.importMetas)
        s3.setResolution(n2, t2, e4);
      s2.has(r2) && r2.namespace.prepare(t2);
    }
  }
  setUpChunkImportsAndExportsForModule(e4) {
    const t2 = new Set(e4.includedImports);
    if (!this.outputOptions.preserveModules && this.includedNamespaces.has(e4)) {
      const s2 = e4.namespace.getMemberVariables();
      for (const e5 of Object.values(s2))
        e5.included && t2.add(e5);
    }
    for (let s2 of t2) {
      s2 instanceof ho && (s2 = s2.getOriginalVariable()), s2 instanceof Eo && (s2 = s2.getBaseVariable());
      const t3 = this.chunkByModule.get(s2.module);
      t3 !== this && (this.imports.add(s2), s2.module instanceof jo && (this.checkCircularDependencyImport(s2, e4), s2 instanceof xo && this.outputOptions.preserveModules || t3.exports.add(s2)));
    }
    (this.includedNamespaces.has(e4) || e4.info.isEntry && false !== e4.preserveSignature || e4.includedDynamicImporters.some((e5) => this.chunkByModule.get(e5) !== this)) && this.ensureReexportsAreAvailableForModule(e4);
    for (const { node: t3, resolution: s2 } of e4.dynamicImports)
      t3.included && s2 instanceof jo && this.chunkByModule.get(s2) === this && !this.includedNamespaces.has(s2) && (this.includedNamespaces.add(s2), this.ensureReexportsAreAvailableForModule(s2));
  }
}
function Ta(e4) {
  return Va(e4) ?? T(e4.id);
}
function Va(e4) {
  var _a2, _b;
  return ((_a2 = e4.chunkNames.find(({ isUserDefined: e5 }) => e5)) == null ? void 0 : _a2.name) ?? ((_b = e4.chunkNames[0]) == null ? void 0 : _b.name);
}
function Ba(e4, t2) {
  const s2 = {};
  for (const [i2, n2] of e4) {
    const e5 = /* @__PURE__ */ new Set();
    if (n2.imports)
      for (const { imported: t3 } of n2.imports)
        e5.add(t3);
    if (n2.reexports)
      for (const { imported: t3 } of n2.reexports)
        e5.add(t3);
    s2[t2(i2)] = [...e5];
  }
  return s2;
}
const za = /[#?]/, Fa = (e4) => e4.getFileName();
function* ja(e4) {
  for (const t2 of e4)
    yield* t2;
}
function Ua(e4, t2, s2, i2) {
  const { chunkDefinitions: n2, modulesInManualChunks: r2 } = function(e5) {
    const t3 = [], s3 = new Set(e5.keys()), i3 = /* @__PURE__ */ Object.create(null);
    for (const [t4, n3] of e5)
      Ga(t4, i3[n3] || (i3[n3] = []), s3);
    for (const [e6, s4] of Object.entries(i3))
      t3.push({ alias: e6, modules: s4 });
    return { chunkDefinitions: t3, modulesInManualChunks: s3 };
  }(t2), { allEntries: o2, dependentEntriesByModule: a2, dynamicallyDependentEntriesByDynamicEntry: l2, dynamicImportsByEntry: c2 } = function(e5) {
    const t3 = /* @__PURE__ */ new Set(), s3 = /* @__PURE__ */ new Map(), i3 = [], n3 = new Set(e5);
    let r3 = 0;
    for (const e6 of n3) {
      const o4 = /* @__PURE__ */ new Set();
      i3.push(o4);
      const a4 = /* @__PURE__ */ new Set([e6]);
      for (const e7 of a4) {
        U(s3, e7, G).add(r3);
        for (const t4 of e7.getDependenciesToBeIncluded())
          t4 instanceof Jt || a4.add(t4);
        for (const { resolution: s4 } of e7.dynamicImports)
          s4 instanceof jo && s4.includedDynamicImporters.length > 0 && !n3.has(s4) && (t3.add(s4), n3.add(s4), o4.add(s4));
        for (const s4 of e7.implicitlyLoadedBefore)
          n3.has(s4) || (t3.add(s4), n3.add(s4));
      }
      r3++;
    }
    const o3 = [...n3], { dynamicEntries: a3, dynamicImportsByEntry: l3 } = function(e6, t4, s4) {
      const i4 = /* @__PURE__ */ new Map(), n4 = /* @__PURE__ */ new Set();
      for (const [s5, r5] of e6.entries())
        i4.set(r5, s5), t4.has(r5) && n4.add(s5);
      const r4 = [];
      for (const e7 of s4) {
        const t5 = /* @__PURE__ */ new Set();
        for (const s5 of e7)
          t5.add(i4.get(s5));
        r4.push(t5);
      }
      return { dynamicEntries: n4, dynamicImportsByEntry: r4 };
    }(o3, t3, i3);
    return { allEntries: o3, dependentEntriesByModule: s3, dynamicallyDependentEntriesByDynamicEntry: Wa(s3, a3, o3), dynamicImportsByEntry: l3 };
  }(e4), h2 = qa(function* (e5, t3) {
    for (const [s3, i3] of e5)
      t3.has(s3) || (yield { dependentEntries: i3, modules: [s3] });
  }(a2, r2));
  return function(e5, t3, s3, i3) {
    const n3 = i3.map(() => 0n), r3 = i3.map((e6, s4) => t3.has(s4) ? -1n : 0n);
    let o3 = 1n;
    for (const { dependentEntries: t4 } of e5) {
      for (const e6 of t4)
        n3[e6] |= o3;
      o3 <<= 1n;
    }
    const a3 = t3;
    for (const [e6, t4] of a3) {
      a3.delete(e6);
      const i4 = r3[e6];
      let o4 = i4;
      for (const e7 of t4)
        o4 &= n3[e7] | r3[e7];
      if (o4 !== i4) {
        r3[e6] = o4;
        for (const t5 of s3[e6])
          U(a3, t5, G).add(e6);
      }
    }
    o3 = 1n;
    for (const { dependentEntries: t4 } of e5) {
      for (const e6 of t4)
        (r3[e6] & o3) === o3 && t4.delete(e6);
      o3 <<= 1n;
    }
  }(h2, l2, c2, o2), n2.push(...function(e5, t3, s3, i3) {
    Oo("optimize chunks", 3);
    const n3 = function(e6, t4, s4) {
      const i4 = [], n4 = [], r3 = /* @__PURE__ */ new Map(), o3 = [];
      let a3 = 0n, l3 = 1n;
      for (const { dependentEntries: t5, modules: c3 } of e6) {
        const e7 = { containedAtoms: l3, correlatedAtoms: 0n, dependencies: /* @__PURE__ */ new Set(), dependentChunks: /* @__PURE__ */ new Set(), dependentEntries: t5, modules: c3, pure: true, size: 0 };
        let h3 = 0, u2 = true;
        for (const t6 of c3)
          r3.set(t6, e7), t6.isIncluded() && (u2 && (u2 = !t6.hasEffects()), h3 += s4 > 1 ? t6.estimateSize() : 1);
        e7.pure = u2, e7.size = h3, o3.push(h3), u2 || (a3 |= l3), (h3 < s4 ? i4 : n4).push(e7), l3 <<= 1n;
      }
      if (0 === i4.length)
        return null;
      return a3 |= function(e7, t5, s5, i5) {
        const n5 = /* @__PURE__ */ new Map();
        let r4 = 0n;
        const o4 = [];
        for (let e8 = 0; e8 < s5; e8++)
          o4.push(0n);
        for (const s6 of e7) {
          s6.sort(Ya);
          for (const e8 of s6) {
            const { dependencies: s7, dependentEntries: a4, modules: l4 } = e8;
            for (const o5 of l4)
              for (const a5 of o5.getDependenciesToBeIncluded())
                if (a5 instanceof Jt)
                  a5.info.moduleSideEffects && (e8.containedAtoms |= U(n5, a5, () => {
                    const e9 = i5;
                    return i5 <<= 1n, r4 |= e9, e9;
                  }));
                else {
                  const i6 = t5.get(a5);
                  i6 && i6 !== e8 && (s7.add(i6), i6.dependentChunks.add(e8));
                }
            const { containedAtoms: c3 } = e8;
            for (const e9 of a4)
              o4[e9] |= c3;
          }
        }
        for (const t6 of e7)
          for (const e8 of t6) {
            const { dependentEntries: t7 } = e8;
            e8.correlatedAtoms = -1n;
            for (const s6 of t7)
              e8.correlatedAtoms &= o4[s6];
          }
        return r4;
      }([n4, i4], r3, t4, l3), { big: new Set(n4), sideEffectAtoms: a3, sizeByAtom: o3, small: new Set(i4) };
    }(e5, t3, s3);
    if (!n3)
      return Do("optimize chunks", 3), e5;
    return s3 > 1 && i3("info", Wt(e5.length, n3.small.size, "Initially")), function(e6, t4) {
      const { small: s4 } = e6;
      for (const i4 of s4) {
        const n4 = Ha(i4, e6, t4 <= 1 ? 1 : 1 / 0);
        if (n4) {
          const { containedAtoms: r3, correlatedAtoms: o3, modules: a3, pure: l3, size: c3 } = i4;
          s4.delete(i4), Ka(n4, t4, e6).delete(n4), n4.modules.push(...a3), n4.size += c3, n4.pure && (n4.pure = l3);
          const { dependencies: h3, dependentChunks: u2, dependentEntries: d2 } = n4;
          n4.correlatedAtoms &= o3, n4.containedAtoms |= r3;
          for (const e7 of i4.dependentEntries)
            d2.add(e7);
          for (const e7 of i4.dependencies)
            h3.add(e7), e7.dependentChunks.delete(i4), e7.dependentChunks.add(n4);
          for (const e7 of i4.dependentChunks)
            u2.add(e7), e7.dependencies.delete(i4), e7.dependencies.add(n4);
          h3.delete(n4), u2.delete(n4), Ka(n4, t4, e6).add(n4);
        }
      }
    }(n3, s3), s3 > 1 && i3("info", Wt(n3.small.size + n3.big.size, n3.small.size, "After merging chunks")), Do("optimize chunks", 3), [...n3.small, ...n3.big];
  }(qa(h2), o2.length, s2, i2).map(({ modules: e5 }) => ({ alias: null, modules: e5 }))), n2;
}
function Ga(e4, t2, s2) {
  const i2 = /* @__PURE__ */ new Set([e4]);
  for (const e5 of i2) {
    s2.add(e5), t2.push(e5);
    for (const t3 of e5.dependencies)
      t3 instanceof Jt || s2.has(t3) || i2.add(t3);
  }
}
function Wa(e4, t2, s2) {
  const i2 = /* @__PURE__ */ new Map();
  for (const n2 of t2) {
    const t3 = U(i2, n2, G), r2 = s2[n2];
    for (const s3 of ja([r2.includedDynamicImporters, r2.implicitlyLoadedAfter]))
      for (const i3 of e4.get(s3))
        t3.add(i3);
  }
  return i2;
}
function qa(e4) {
  var t2;
  const s2 = /* @__PURE__ */ Object.create(null);
  for (const { dependentEntries: i2, modules: n2 } of e4) {
    let e5 = 0n;
    for (const t3 of i2)
      e5 |= 1n << BigInt(t3);
    (s2[t2 = String(e5)] || (s2[t2] = { dependentEntries: new Set(i2), modules: [] })).modules.push(...n2);
  }
  return Object.values(s2);
}
function Ha(e4, { big: t2, sideEffectAtoms: s2, sizeByAtom: i2, small: n2 }, r2) {
  let o2 = null;
  for (const a2 of ja([n2, t2])) {
    if (e4 === a2)
      continue;
    const t3 = Xa(e4, a2, r2, s2, i2);
    if (t3 < r2) {
      if (o2 = a2, 0 === t3)
        break;
      r2 = t3;
    }
  }
  return o2;
}
function Ka(e4, t2, s2) {
  return e4.size < t2 ? s2.small : s2.big;
}
function Ya({ size: e4 }, { size: t2 }) {
  return e4 - t2;
}
function Xa(e4, t2, s2, i2, n2) {
  const r2 = Qa(e4, t2, s2, i2, n2);
  return r2 < s2 ? r2 + Qa(t2, e4, s2 - r2, i2, n2) : 1 / 0;
}
function Qa(e4, t2, s2, i2, n2) {
  const { correlatedAtoms: r2 } = t2;
  let o2 = e4.containedAtoms;
  const a2 = o2 & i2;
  if ((r2 & a2) !== a2)
    return 1 / 0;
  const l2 = new Set(e4.dependencies);
  for (const { dependencies: e5, containedAtoms: s3 } of l2) {
    o2 |= s3;
    const n3 = s3 & i2;
    if ((r2 & n3) !== n3)
      return 1 / 0;
    for (const s4 of e5) {
      if (s4 === t2)
        return 1 / 0;
      l2.add(s4);
    }
  }
  return function(e5, t3, s3) {
    let i3 = 0, n3 = 0, r3 = 1n;
    const { length: o3 } = s3;
    for (; n3 < o3; n3++)
      if ((e5 & r3) === r3 && (i3 += s3[n3]), r3 <<= 1n, i3 >= t3)
        return 1 / 0;
    return i3;
  }(o2 & ~r2, s2, n2);
}
const Za = (e4, t2) => e4.execIndex > t2.execIndex ? 1 : -1;
function Ja(e4, t2, s2) {
  const i2 = Symbol(e4.id), n2 = [e4.id];
  let r2 = t2;
  for (e4.cycles.add(i2); r2 !== e4; )
    r2.cycles.add(i2), n2.push(r2.id), r2 = s2.get(r2);
  return n2.push(n2[0]), n2.reverse(), n2;
}
const el = (e4, t2) => t2 ? `(${e4})` : e4, tl = /^(?!\d)[\w$]+$/;
class sl {
  constructor(e4, t2) {
    this.isOriginal = true, this.filename = e4, this.content = t2;
  }
  traceSegment(e4, t2, s2) {
    return { column: t2, line: e4, name: s2, source: this };
  }
}
class il {
  constructor(e4, t2) {
    this.sources = t2, this.names = e4.names, this.mappings = e4.mappings;
  }
  traceMappings() {
    const e4 = [], t2 = /* @__PURE__ */ new Map(), s2 = [], i2 = [], n2 = /* @__PURE__ */ new Map(), r2 = [];
    for (const o2 of this.mappings) {
      const a2 = [];
      for (const r3 of o2) {
        if (1 === r3.length)
          continue;
        const o3 = this.sources[r3[1]];
        if (!o3)
          continue;
        const l2 = o3.traceSegment(r3[2], r3[3], 5 === r3.length ? this.names[r3[4]] : "");
        if (l2) {
          const { column: o4, line: c2, name: h2, source: { content: u2, filename: d2 } } = l2;
          let p2 = t2.get(d2);
          if (void 0 === p2)
            p2 = e4.length, e4.push(d2), t2.set(d2, p2), s2[p2] = u2;
          else if (null == s2[p2])
            s2[p2] = u2;
          else if (null != u2 && s2[p2] !== u2)
            return Qe(Ht(d2));
          const f2 = [r3[0], p2, c2, o4];
          if (h2) {
            let e5 = n2.get(h2);
            void 0 === e5 && (e5 = i2.length, i2.push(h2), n2.set(h2, e5)), f2[4] = e5;
          }
          a2.push(f2);
        }
      }
      r2.push(a2);
    }
    return { mappings: r2, names: i2, sources: e4, sourcesContent: s2 };
  }
  traceSegment(e4, t2, s2) {
    const i2 = this.mappings[e4];
    if (!i2)
      return null;
    let n2 = 0, r2 = i2.length - 1;
    for (; n2 <= r2; ) {
      const e5 = n2 + r2 >> 1, o2 = i2[e5];
      if (o2[0] === t2 || n2 === r2) {
        if (1 == o2.length)
          return null;
        const e6 = this.sources[o2[1]];
        return e6 ? e6.traceSegment(o2[2], o2[3], 5 === o2.length ? this.names[o2[4]] : s2) : null;
      }
      o2[0] > t2 ? r2 = e5 - 1 : n2 = e5 + 1;
    }
    return null;
  }
}
function nl(e4) {
  return function(t2, s2) {
    return s2.missing ? (e4(Ae, (i2 = s2.plugin, { code: Pt, message: `Sourcemap is likely to be incorrect: a plugin (${i2}) was used to transform files, but didn't generate a sourcemap for the transformation. Consult the plugin documentation for help`, plugin: i2, url: De(Te) })), new il({ mappings: [], names: [] }, [t2])) : new il(s2, [t2]);
    var i2;
  };
}
function rl(e4, t2, s2, i2, n2) {
  let r2;
  if (s2) {
    const t3 = s2.sources, i3 = s2.sourcesContent || [], n3 = $(e4) || ".", o2 = s2.sourceRoot || ".", a2 = t3.map((e5, t4) => new sl(R(n3, o2, e5), i3[t4]));
    r2 = new il(s2, a2);
  } else
    r2 = new sl(e4, t2);
  return i2.reduce(n2, r2);
}
var ol = {}, al = ll;
function ll(e4, t2) {
  if (!e4)
    throw new Error(t2 || "Assertion failed");
}
ll.equal = function(e4, t2, s2) {
  if (e4 != t2)
    throw new Error(s2 || "Assertion failed: " + e4 + " != " + t2);
};
var cl = { exports: {} };
"function" == typeof Object.create ? cl.exports = function(e4, t2) {
  t2 && (e4.super_ = t2, e4.prototype = Object.create(t2.prototype, { constructor: { value: e4, enumerable: false, writable: true, configurable: true } }));
} : cl.exports = function(e4, t2) {
  if (t2) {
    e4.super_ = t2;
    var s2 = function() {
    };
    s2.prototype = t2.prototype, e4.prototype = new s2(), e4.prototype.constructor = e4;
  }
};
var hl = cl.exports, ul = al, dl = hl;
function pl(e4, t2) {
  return 55296 == (64512 & e4.charCodeAt(t2)) && (!(t2 < 0 || t2 + 1 >= e4.length) && 56320 == (64512 & e4.charCodeAt(t2 + 1)));
}
function fl(e4) {
  return (e4 >>> 24 | e4 >>> 8 & 65280 | e4 << 8 & 16711680 | (255 & e4) << 24) >>> 0;
}
function ml(e4) {
  return 1 === e4.length ? "0" + e4 : e4;
}
function gl(e4) {
  return 7 === e4.length ? "0" + e4 : 6 === e4.length ? "00" + e4 : 5 === e4.length ? "000" + e4 : 4 === e4.length ? "0000" + e4 : 3 === e4.length ? "00000" + e4 : 2 === e4.length ? "000000" + e4 : 1 === e4.length ? "0000000" + e4 : e4;
}
ol.inherits = dl, ol.toArray = function(e4, t2) {
  if (Array.isArray(e4))
    return e4.slice();
  if (!e4)
    return [];
  var s2 = [];
  if ("string" == typeof e4)
    if (t2) {
      if ("hex" === t2)
        for ((e4 = e4.replace(/[^a-z0-9]+/gi, "")).length % 2 != 0 && (e4 = "0" + e4), n2 = 0; n2 < e4.length; n2 += 2)
          s2.push(parseInt(e4[n2] + e4[n2 + 1], 16));
    } else
      for (var i2 = 0, n2 = 0; n2 < e4.length; n2++) {
        var r2 = e4.charCodeAt(n2);
        r2 < 128 ? s2[i2++] = r2 : r2 < 2048 ? (s2[i2++] = r2 >> 6 | 192, s2[i2++] = 63 & r2 | 128) : pl(e4, n2) ? (r2 = 65536 + ((1023 & r2) << 10) + (1023 & e4.charCodeAt(++n2)), s2[i2++] = r2 >> 18 | 240, s2[i2++] = r2 >> 12 & 63 | 128, s2[i2++] = r2 >> 6 & 63 | 128, s2[i2++] = 63 & r2 | 128) : (s2[i2++] = r2 >> 12 | 224, s2[i2++] = r2 >> 6 & 63 | 128, s2[i2++] = 63 & r2 | 128);
      }
  else
    for (n2 = 0; n2 < e4.length; n2++)
      s2[n2] = 0 | e4[n2];
  return s2;
}, ol.toHex = function(e4) {
  for (var t2 = "", s2 = 0; s2 < e4.length; s2++)
    t2 += ml(e4[s2].toString(16));
  return t2;
}, ol.htonl = fl, ol.toHex32 = function(e4, t2) {
  for (var s2 = "", i2 = 0; i2 < e4.length; i2++) {
    var n2 = e4[i2];
    "little" === t2 && (n2 = fl(n2)), s2 += gl(n2.toString(16));
  }
  return s2;
}, ol.zero2 = ml, ol.zero8 = gl, ol.join32 = function(e4, t2, s2, i2) {
  var n2 = s2 - t2;
  ul(n2 % 4 == 0);
  for (var r2 = new Array(n2 / 4), o2 = 0, a2 = t2; o2 < r2.length; o2++, a2 += 4) {
    var l2;
    l2 = "big" === i2 ? e4[a2] << 24 | e4[a2 + 1] << 16 | e4[a2 + 2] << 8 | e4[a2 + 3] : e4[a2 + 3] << 24 | e4[a2 + 2] << 16 | e4[a2 + 1] << 8 | e4[a2], r2[o2] = l2 >>> 0;
  }
  return r2;
}, ol.split32 = function(e4, t2) {
  for (var s2 = new Array(4 * e4.length), i2 = 0, n2 = 0; i2 < e4.length; i2++, n2 += 4) {
    var r2 = e4[i2];
    "big" === t2 ? (s2[n2] = r2 >>> 24, s2[n2 + 1] = r2 >>> 16 & 255, s2[n2 + 2] = r2 >>> 8 & 255, s2[n2 + 3] = 255 & r2) : (s2[n2 + 3] = r2 >>> 24, s2[n2 + 2] = r2 >>> 16 & 255, s2[n2 + 1] = r2 >>> 8 & 255, s2[n2] = 255 & r2);
  }
  return s2;
}, ol.rotr32 = function(e4, t2) {
  return e4 >>> t2 | e4 << 32 - t2;
}, ol.rotl32 = function(e4, t2) {
  return e4 << t2 | e4 >>> 32 - t2;
}, ol.sum32 = function(e4, t2) {
  return e4 + t2 >>> 0;
}, ol.sum32_3 = function(e4, t2, s2) {
  return e4 + t2 + s2 >>> 0;
}, ol.sum32_4 = function(e4, t2, s2, i2) {
  return e4 + t2 + s2 + i2 >>> 0;
}, ol.sum32_5 = function(e4, t2, s2, i2, n2) {
  return e4 + t2 + s2 + i2 + n2 >>> 0;
}, ol.sum64 = function(e4, t2, s2, i2) {
  var n2 = e4[t2], r2 = i2 + e4[t2 + 1] >>> 0, o2 = (r2 < i2 ? 1 : 0) + s2 + n2;
  e4[t2] = o2 >>> 0, e4[t2 + 1] = r2;
}, ol.sum64_hi = function(e4, t2, s2, i2) {
  return (t2 + i2 >>> 0 < t2 ? 1 : 0) + e4 + s2 >>> 0;
}, ol.sum64_lo = function(e4, t2, s2, i2) {
  return t2 + i2 >>> 0;
}, ol.sum64_4_hi = function(e4, t2, s2, i2, n2, r2, o2, a2) {
  var l2 = 0, c2 = t2;
  return l2 += (c2 = c2 + i2 >>> 0) < t2 ? 1 : 0, l2 += (c2 = c2 + r2 >>> 0) < r2 ? 1 : 0, e4 + s2 + n2 + o2 + (l2 += (c2 = c2 + a2 >>> 0) < a2 ? 1 : 0) >>> 0;
}, ol.sum64_4_lo = function(e4, t2, s2, i2, n2, r2, o2, a2) {
  return t2 + i2 + r2 + a2 >>> 0;
}, ol.sum64_5_hi = function(e4, t2, s2, i2, n2, r2, o2, a2, l2, c2) {
  var h2 = 0, u2 = t2;
  return h2 += (u2 = u2 + i2 >>> 0) < t2 ? 1 : 0, h2 += (u2 = u2 + r2 >>> 0) < r2 ? 1 : 0, h2 += (u2 = u2 + a2 >>> 0) < a2 ? 1 : 0, e4 + s2 + n2 + o2 + l2 + (h2 += (u2 = u2 + c2 >>> 0) < c2 ? 1 : 0) >>> 0;
}, ol.sum64_5_lo = function(e4, t2, s2, i2, n2, r2, o2, a2, l2, c2) {
  return t2 + i2 + r2 + a2 + c2 >>> 0;
}, ol.rotr64_hi = function(e4, t2, s2) {
  return (t2 << 32 - s2 | e4 >>> s2) >>> 0;
}, ol.rotr64_lo = function(e4, t2, s2) {
  return (e4 << 32 - s2 | t2 >>> s2) >>> 0;
}, ol.shr64_hi = function(e4, t2, s2) {
  return e4 >>> s2;
}, ol.shr64_lo = function(e4, t2, s2) {
  return (e4 << 32 - s2 | t2 >>> s2) >>> 0;
};
var yl = {}, xl = ol, El = al;
function bl() {
  this.pending = null, this.pendingTotal = 0, this.blockSize = this.constructor.blockSize, this.outSize = this.constructor.outSize, this.hmacStrength = this.constructor.hmacStrength, this.padLength = this.constructor.padLength / 8, this.endian = "big", this._delta8 = this.blockSize / 8, this._delta32 = this.blockSize / 32;
}
yl.BlockHash = bl, bl.prototype.update = function(e4, t2) {
  if (e4 = xl.toArray(e4, t2), this.pending ? this.pending = this.pending.concat(e4) : this.pending = e4, this.pendingTotal += e4.length, this.pending.length >= this._delta8) {
    var s2 = (e4 = this.pending).length % this._delta8;
    this.pending = e4.slice(e4.length - s2, e4.length), 0 === this.pending.length && (this.pending = null), e4 = xl.join32(e4, 0, e4.length - s2, this.endian);
    for (var i2 = 0; i2 < e4.length; i2 += this._delta32)
      this._update(e4, i2, i2 + this._delta32);
  }
  return this;
}, bl.prototype.digest = function(e4) {
  return this.update(this._pad()), El(null === this.pending), this._digest(e4);
}, bl.prototype._pad = function() {
  var e4 = this.pendingTotal, t2 = this._delta8, s2 = t2 - (e4 + this.padLength) % t2, i2 = new Array(s2 + this.padLength);
  i2[0] = 128;
  for (var n2 = 1; n2 < s2; n2++)
    i2[n2] = 0;
  if (e4 <<= 3, "big" === this.endian) {
    for (var r2 = 8; r2 < this.padLength; r2++)
      i2[n2++] = 0;
    i2[n2++] = 0, i2[n2++] = 0, i2[n2++] = 0, i2[n2++] = 0, i2[n2++] = e4 >>> 24 & 255, i2[n2++] = e4 >>> 16 & 255, i2[n2++] = e4 >>> 8 & 255, i2[n2++] = 255 & e4;
  } else
    for (i2[n2++] = 255 & e4, i2[n2++] = e4 >>> 8 & 255, i2[n2++] = e4 >>> 16 & 255, i2[n2++] = e4 >>> 24 & 255, i2[n2++] = 0, i2[n2++] = 0, i2[n2++] = 0, i2[n2++] = 0, r2 = 8; r2 < this.padLength; r2++)
      i2[n2++] = 0;
  return i2;
};
var vl = {}, Sl = ol.rotr32;
function Al(e4, t2, s2) {
  return e4 & t2 ^ ~e4 & s2;
}
function kl(e4, t2, s2) {
  return e4 & t2 ^ e4 & s2 ^ t2 & s2;
}
function Il(e4, t2, s2) {
  return e4 ^ t2 ^ s2;
}
vl.ft_1 = function(e4, t2, s2, i2) {
  return 0 === e4 ? Al(t2, s2, i2) : 1 === e4 || 3 === e4 ? Il(t2, s2, i2) : 2 === e4 ? kl(t2, s2, i2) : void 0;
}, vl.ch32 = Al, vl.maj32 = kl, vl.p32 = Il, vl.s0_256 = function(e4) {
  return Sl(e4, 2) ^ Sl(e4, 13) ^ Sl(e4, 22);
}, vl.s1_256 = function(e4) {
  return Sl(e4, 6) ^ Sl(e4, 11) ^ Sl(e4, 25);
}, vl.g0_256 = function(e4) {
  return Sl(e4, 7) ^ Sl(e4, 18) ^ e4 >>> 3;
}, vl.g1_256 = function(e4) {
  return Sl(e4, 17) ^ Sl(e4, 19) ^ e4 >>> 10;
};
var wl = ol, Pl = yl, Cl = vl, $l = al, Nl = wl.sum32, _l = wl.sum32_4, Rl = wl.sum32_5, Ml = Cl.ch32, Ol = Cl.maj32, Dl = Cl.s0_256, Ll = Cl.s1_256, Tl = Cl.g0_256, Vl = Cl.g1_256, Bl = Pl.BlockHash, zl = [1116352408, 1899447441, 3049323471, 3921009573, 961987163, 1508970993, 2453635748, 2870763221, 3624381080, 310598401, 607225278, 1426881987, 1925078388, 2162078206, 2614888103, 3248222580, 3835390401, 4022224774, 264347078, 604807628, 770255983, 1249150122, 1555081692, 1996064986, 2554220882, 2821834349, 2952996808, 3210313671, 3336571891, 3584528711, 113926993, 338241895, 666307205, 773529912, 1294757372, 1396182291, 1695183700, 1986661051, 2177026350, 2456956037, 2730485921, 2820302411, 3259730800, 3345764771, 3516065817, 3600352804, 4094571909, 275423344, 430227734, 506948616, 659060556, 883997877, 958139571, 1322822218, 1537002063, 1747873779, 1955562222, 2024104815, 2227730452, 2361852424, 2428436474, 2756734187, 3204031479, 3329325298];
function Fl() {
  if (!(this instanceof Fl))
    return new Fl();
  Bl.call(this), this.h = [1779033703, 3144134277, 1013904242, 2773480762, 1359893119, 2600822924, 528734635, 1541459225], this.k = zl, this.W = new Array(64);
}
wl.inherits(Fl, Bl);
var jl = Fl;
Fl.blockSize = 512, Fl.outSize = 256, Fl.hmacStrength = 192, Fl.padLength = 64, Fl.prototype._update = function(e4, t2) {
  for (var s2 = this.W, i2 = 0; i2 < 16; i2++)
    s2[i2] = e4[t2 + i2];
  for (; i2 < s2.length; i2++)
    s2[i2] = _l(Vl(s2[i2 - 2]), s2[i2 - 7], Tl(s2[i2 - 15]), s2[i2 - 16]);
  var n2 = this.h[0], r2 = this.h[1], o2 = this.h[2], a2 = this.h[3], l2 = this.h[4], c2 = this.h[5], h2 = this.h[6], u2 = this.h[7];
  for ($l(this.k.length === s2.length), i2 = 0; i2 < s2.length; i2++) {
    var d2 = Rl(u2, Ll(l2), Ml(l2, c2, h2), this.k[i2], s2[i2]), p2 = Nl(Dl(n2), Ol(n2, r2, o2));
    u2 = h2, h2 = c2, c2 = l2, l2 = Nl(a2, d2), a2 = o2, o2 = r2, r2 = n2, n2 = Nl(d2, p2);
  }
  this.h[0] = Nl(this.h[0], n2), this.h[1] = Nl(this.h[1], r2), this.h[2] = Nl(this.h[2], o2), this.h[3] = Nl(this.h[3], a2), this.h[4] = Nl(this.h[4], l2), this.h[5] = Nl(this.h[5], c2), this.h[6] = Nl(this.h[6], h2), this.h[7] = Nl(this.h[7], u2);
}, Fl.prototype._digest = function(e4) {
  return "hex" === e4 ? wl.toHex32(this.h, "big") : wl.split32(this.h, "big");
};
var Ul = t(jl);
const Gl = () => Ul();
async function Wl(e4, t2, s2, i2, n2) {
  Oo("render chunks", 2), function(e5) {
    for (const t3 of e5)
      t3.facadeModule && t3.facadeModule.isUserDefinedEntryPoint && t3.getPreliminaryFileName();
  }(e4);
  const r2 = await Promise.all(e4.map((e5) => e5.render()));
  Do("render chunks", 2), Oo("transform chunks", 2);
  const o2 = function(e5) {
    return Object.fromEntries(e5.map((e6) => {
      const t3 = e6.getRenderedChunkInfo();
      return [t3.fileName, t3];
    }));
  }(e4), { initialHashesByPlaceholder: a2, nonHashedChunksWithPlaceholders: l2, renderedChunksByPlaceholder: c2, hashDependenciesByPlaceholder: h2 } = await async function(e5, t3, s3, i3, n3) {
    const r3 = [], o3 = /* @__PURE__ */ new Map(), a3 = /* @__PURE__ */ new Map(), l3 = /* @__PURE__ */ new Map(), c3 = /* @__PURE__ */ new Set();
    for (const { preliminaryFileName: { hashPlaceholder: t4 } } of e5)
      t4 && c3.add(t4);
    return await Promise.all(e5.map(async ({ chunk: e6, preliminaryFileName: { fileName: h3, hashPlaceholder: u3 }, preliminarySourcemapFileName: d2, magicString: p2, usedModules: f2 }) => {
      const m2 = { chunk: e6, fileName: h3, sourcemapFileName: (d2 == null ? void 0 : d2.fileName) ?? null, ...await ql(p2, h3, f2, t3, s3, i3, n3) }, { code: g2, map: y2 } = m2;
      if (u3) {
        const { containedPlaceholders: t4, transformedCode: s4 } = $a(g2, c3), n4 = Gl().update(s4), r4 = i3.hookReduceValueSync("augmentChunkHash", "", [e6.getRenderedChunkInfo()], (e7, t5) => (t5 && (e7 += t5), e7));
        r4 && n4.update(r4), o3.set(u3, m2), a3.set(u3, { containedPlaceholders: t4, contentHash: n4.digest("hex") });
      } else
        r3.push(m2);
      const x2 = d2 == null ? void 0 : d2.hashPlaceholder;
      y2 && x2 && l3.set(d2.hashPlaceholder, Gl().update(y2.toString()).digest("hex").slice(0, d2.hashPlaceholder.length));
    })), { hashDependenciesByPlaceholder: a3, initialHashesByPlaceholder: l3, nonHashedChunksWithPlaceholders: r3, renderedChunksByPlaceholder: o3 };
  }(r2, o2, i2, s2, n2), u2 = function(e5, t3, s3, i3) {
    const n3 = new Map(s3);
    for (const [s4, { fileName: r3 }] of e5) {
      let e6 = Gl();
      const o3 = /* @__PURE__ */ new Set([s4]);
      for (const s5 of o3) {
        const { containedPlaceholders: i4, contentHash: n4 } = t3.get(s5);
        e6.update(n4);
        for (const e7 of i4)
          o3.add(e7);
      }
      let a3, l3;
      do {
        l3 && (e6 = Gl().update(l3)), l3 = e6.digest("hex").slice(0, s4.length), a3 = Ca(r3, s4, l3);
      } while (i3[Na].has(a3.toLowerCase()));
      i3[a3] = _a, n3.set(s4, l3);
    }
    return n3;
  }(c2, h2, a2, t2);
  !function(e5, t3, s3, i3, n3, r3) {
    for (const { chunk: i4, code: o3, fileName: a3, sourcemapFileName: l3, map: c3 } of e5.values()) {
      let e6 = Pa(o3, t3);
      const h3 = Pa(a3, t3);
      let u3 = null;
      c3 && (u3 = l3 ? Pa(l3, t3) : `${h3}.map`, c3.file = Pa(c3.file, t3), e6 += Hl(u3, c3, n3, r3)), s3[h3] = i4.finalizeChunk(e6, c3, u3, t3);
    }
    for (const { chunk: e6, code: o3, fileName: a3, sourcemapFileName: l3, map: c3 } of i3) {
      let i4 = t3.size > 0 ? Pa(o3, t3) : o3, h3 = null;
      c3 && (h3 = l3 ? Pa(l3, t3) : `${a3}.map`, i4 += Hl(h3, c3, n3, r3)), s3[a3] = e6.finalizeChunk(i4, c3, h3, t3);
    }
  }(c2, u2, t2, l2, s2, i2), Do("transform chunks", 2);
}
async function ql(e4, t2, s2, i2, n2, r2, o2) {
  let a2 = null;
  const c2 = [];
  let h2 = await r2.hookReduceArg0("renderChunk", [e4.toString(), i2[t2], n2, { chunks: i2 }], (e5, t3, s3) => {
    if (null == t3)
      return e5;
    if ("string" == typeof t3 && (t3 = { code: t3, map: void 0 }), null !== t3.map) {
      const e6 = Ao(t3.map);
      c2.push(e6 || { missing: true, plugin: s3.name });
    }
    return t3.code;
  });
  const { compact: u2, dir: d2, file: p2, sourcemap: f2, sourcemapExcludeSources: m2, sourcemapFile: g2, sourcemapPathTransform: y2, sourcemapIgnoreList: x2 } = n2;
  if (u2 || "\n" === h2[h2.length - 1] || (h2 += "\n"), f2) {
    let i3;
    Oo("sourcemaps", 3), i3 = p2 ? R(g2 || p2) : d2 ? R(d2, t2) : R(t2);
    a2 = function(e5, t3, s3, i4, n3, r3) {
      const o3 = nl(r3), a3 = s3.filter((e6) => !e6.excludeFromSourcemap).map((e6) => rl(e6.id, e6.originalCode, e6.originalSourcemap, e6.sourcemapChain, o3)), c3 = new il(t3, a3), h3 = i4.reduce(o3, c3);
      let { sources: u3, sourcesContent: d3, names: p3, mappings: f3 } = h3.traceMappings();
      if (e5) {
        const t4 = $(e5);
        u3 = u3.map((e6) => _(t4, e6)), e5 = C(e5);
      }
      d3 = n3 ? null : d3;
      for (const e6 of s3)
        So(e6.originalSourcemap, e6.sourcemapChain);
      return new l({ file: e5, mappings: f3, names: p3, sources: u3, sourcesContent: d3 });
    }(i3, e4.generateDecodedMap({}), s2, c2, m2, o2);
    for (let e5 = 0; e5 < a2.sources.length; ++e5) {
      let t3 = a2.sources[e5];
      const s3 = `${i3}.map`, n3 = x2(t3, s3);
      "boolean" != typeof n3 && Qe(Xt("sourcemapIgnoreList function must return a boolean.")), n3 && (void 0 === a2.x_google_ignoreList && (a2.x_google_ignoreList = []), a2.x_google_ignoreList.includes(e5) || a2.x_google_ignoreList.push(e5)), y2 && (t3 = y2(t3, s3), "string" != typeof t3 && Qe(Xt("sourcemapPathTransform function must return a string."))), a2.sources[e5] = P(t3);
    }
    Do("sourcemaps", 3);
  }
  return { code: h2, map: a2 };
}
function Hl(e4, t2, s2, { sourcemap: i2, sourcemapBaseUrl: n2 }) {
  let r2;
  if ("inline" === i2)
    r2 = t2.toUrl();
  else {
    const i3 = C(e4);
    r2 = n2 ? new URL(i3, n2).toString() : i3, s2.emitFile({ fileName: e4, source: t2.toString(), type: "asset" });
  }
  return "hidden" === i2 ? "" : `//# ${Fs}=${r2}
`;
}
class Kl {
  constructor(e4, t2, s2, i2, n2) {
    this.outputOptions = e4, this.unsetOptions = t2, this.inputOptions = s2, this.pluginDriver = i2, this.graph = n2, this.facadeChunkByModule = /* @__PURE__ */ new Map(), this.includedNamespaces = /* @__PURE__ */ new Set();
  }
  async generate(e4) {
    Oo("GENERATE", 1);
    const t2 = /* @__PURE__ */ Object.create(null), s2 = ((e5) => {
      const t3 = /* @__PURE__ */ new Set();
      return new Proxy(e5, { deleteProperty: (e6, s3) => ("string" == typeof s3 && t3.delete(s3.toLowerCase()), Reflect.deleteProperty(e6, s3)), get: (e6, s3) => s3 === Na ? t3 : Reflect.get(e6, s3), set: (e6, s3, i2) => ("string" == typeof s3 && t3.add(s3.toLowerCase()), Reflect.set(e6, s3, i2)) });
    })(t2);
    this.pluginDriver.setOutputBundle(s2, this.outputOptions);
    try {
      Oo("initialize render", 2), await this.pluginDriver.hookParallel("renderStart", [this.outputOptions, this.inputOptions]), Do("initialize render", 2), Oo("generate chunks", 2);
      const e5 = (() => {
        let e6 = 0;
        return (t4, s3 = 8) => {
          if (s3 > 64)
            return Qe(Xt(`Hashes cannot be longer than 64 characters, received ${s3}. Check the "${t4}" option.`));
          const i2 = `${ka}${Di(++e6).padStart(s3 - 5, "0")}${Ia}`;
          return i2.length > s3 ? Qe(Xt(`To generate hashes for this number of chunks (currently ${e6}), you need a minimum hash size of ${i2.length}, received ${s3}. Check the "${t4}" option.`)) : i2;
        };
      })(), t3 = await this.generateChunks(s2, e5);
      t3.length > 1 && function(e6, t4) {
        if ("umd" === e6.format || "iife" === e6.format)
          return Qe(jt("output.format", je, "UMD and IIFE output formats are not supported for code-splitting builds", e6.format));
        if ("string" == typeof e6.file)
          return Qe(jt("output.file", Be, 'when building multiple chunks, the "output.dir" option must be used, not "output.file". To inline dynamic imports, set the "inlineDynamicImports" option'));
        if (e6.sourcemapFile)
          return Qe(jt("output.sourcemapFile", Ye, '"output.sourcemapFile" is only supported for single-file builds'));
        !e6.amd.autoId && e6.amd.id && t4(Ae, jt("output.amd.id", Ve, 'this option is only properly supported for single-file builds. Use "output.amd.autoId" and "output.amd.basePath" instead'));
      }(this.outputOptions, this.inputOptions.onLog), this.pluginDriver.setChunkInformation(this.facadeChunkByModule);
      for (const e6 of t3)
        e6.generateExports();
      Do("generate chunks", 2), await Wl(t3, s2, this.pluginDriver, this.outputOptions, this.inputOptions.onLog);
    } catch (e5) {
      throw await this.pluginDriver.hookParallel("renderError", [e5]), e5;
    }
    return ((e5) => {
      const t3 = /* @__PURE__ */ new Set(), s3 = Object.values(e5);
      for (const e6 of s3)
        "asset" === e6.type && e6.needsCodeReference && t3.add(e6.fileName);
      for (const e6 of s3)
        if ("chunk" === e6.type)
          for (const s4 of e6.referencedFiles)
            t3.has(s4) && t3.delete(s4);
      for (const s4 of t3)
        delete e5[s4];
    })(s2), Oo("generate bundle", 2), await this.pluginDriver.hookSeq("generateBundle", [this.outputOptions, s2, e4]), this.finaliseAssets(s2), Do("generate bundle", 2), Do("GENERATE", 1), t2;
  }
  async addManualChunks(e4) {
    const t2 = /* @__PURE__ */ new Map(), s2 = await Promise.all(Object.entries(e4).map(async ([e5, t3]) => ({ alias: e5, entries: await this.graph.moduleLoader.addAdditionalModules(t3, true) })));
    for (const { alias: e5, entries: i2 } of s2)
      for (const s3 of i2)
        Yl(e5, s3, t2);
    return t2;
  }
  assignManualChunks(e4) {
    const t2 = [], s2 = { getModuleIds: () => this.graph.modulesById.keys(), getModuleInfo: this.graph.getModuleInfo };
    for (const i3 of this.graph.modulesById.values())
      if (i3 instanceof jo) {
        const n2 = e4(i3.id, s2);
        "string" == typeof n2 && t2.push([n2, i3]);
      }
    t2.sort(([e5], [t3]) => e5 > t3 ? 1 : e5 < t3 ? -1 : 0);
    const i2 = /* @__PURE__ */ new Map();
    for (const [e5, s3] of t2)
      Yl(e5, s3, i2);
    return i2;
  }
  finaliseAssets(e4) {
    if (this.outputOptions.validate) {
      for (const t2 of Object.values(e4))
        if ("code" in t2)
          try {
            this.graph.contextParse(t2.code, { ecmaVersion: "latest" });
          } catch (e5) {
            this.inputOptions.onLog(Ae, Dt(t2, e5));
          }
    }
    this.pluginDriver.finaliseAssets();
  }
  async generateChunks(e4, t2) {
    const { experimentalMinChunkSize: s2, inlineDynamicImports: i2, manualChunks: n2, preserveModules: r2 } = this.outputOptions, o2 = "object" == typeof n2 ? await this.addManualChunks(n2) : this.assignManualChunks(n2), a2 = function({ compact: e5, generatedCode: { arrowFunctions: t3, constBindings: s3, objectShorthand: i3, reservedNamesAsProps: n3 } }) {
      const { _: r3, n: o3, s: a3 } = e5 ? { _: "", n: "", s: "" } : { _: " ", n: "\n", s: ";" }, l3 = s3 ? "const" : "var", c3 = (e6, { isAsync: t4, name: s4 }) => `${t4 ? "async " : ""}function${s4 ? ` ${s4}` : ""}${r3}(${e6.join(`,${r3}`)})${r3}`, h3 = t3 ? (e6, { isAsync: t4, name: s4 }) => {
        const i4 = 1 === e6.length;
        return `${s4 ? `${l3} ${s4}${r3}=${r3}` : ""}${t4 ? `async${i4 ? " " : r3}` : ""}${i4 ? e6[0] : `(${e6.join(`,${r3}`)})`}${r3}=>${r3}`;
      } : c3, u3 = (e6, { functionReturn: s4, lineBreakIndent: i4, name: n4 }) => [`${h3(e6, { isAsync: false, name: n4 })}${t3 ? i4 ? `${o3}${i4.base}${i4.t}` : "" : `{${i4 ? `${o3}${i4.base}${i4.t}` : r3}${s4 ? "return " : ""}`}`, t3 ? `${n4 ? ";" : ""}${i4 ? `${o3}${i4.base}` : ""}` : `${a3}${i4 ? `${o3}${i4.base}` : r3}}`], d3 = n3 ? (e6) => tl.test(e6) : (e6) => !Ee.has(e6) && tl.test(e6);
      return { _: r3, cnst: l3, getDirectReturnFunction: u3, getDirectReturnIifeLeft: (e6, s4, { needsArrowReturnParens: i4, needsWrappedFunction: n4 }) => {
        const [r4, o4] = u3(e6, { functionReturn: true, lineBreakIndent: null, name: null });
        return `${el(`${r4}${el(s4, t3 && i4)}${o4}`, t3 || n4)}(`;
      }, getFunctionIntro: h3, getNonArrowFunctionIntro: c3, getObject(e6, { lineBreakIndent: t4 }) {
        const s4 = t4 ? `${o3}${t4.base}${t4.t}` : r3;
        return `{${e6.map(([e7, t5]) => {
          if (null === e7)
            return `${s4}${t5}`;
          const n4 = !d3(e7);
          return e7 === t5 && i3 && !n4 ? s4 + e7 : `${s4}${n4 ? `'${e7}'` : e7}:${r3}${t5}`;
        }).join(",")}${0 === e6.length ? "" : t4 ? `${o3}${t4.base}` : r3}}`;
      }, getPropertyAccess: (e6) => d3(e6) ? `.${e6}` : `[${JSON.stringify(e6)}]`, n: o3, s: a3 };
    }(this.outputOptions), l2 = function(e5) {
      const t3 = [];
      for (const s3 of e5.values())
        s3 instanceof jo && (s3.isIncluded() || s3.info.isEntry || s3.includedDynamicImporters.length > 0) && t3.push(s3);
      return t3;
    }(this.graph.modulesById), c2 = function(e5) {
      if (0 === e5.length)
        return "/";
      if (1 === e5.length)
        return $(e5[0]);
      const t3 = e5.slice(1).reduce((e6, t4) => {
        const s3 = t4.split(/\/+|\\+/);
        let i3;
        for (i3 = 0; e6[i3] === s3[i3] && i3 < Math.min(e6.length, s3.length); i3++)
          ;
        return e6.slice(0, i3);
      }, e5[0].split(/\/+|\\+/));
      return t3.length > 1 ? t3.join("/") : "/";
    }(function(e5, t3) {
      const s3 = [];
      for (const i3 of e5)
        (i3.info.isEntry || t3) && I(i3.id) && s3.push(i3.id);
      return s3;
    }(l2, r2)), h2 = function(e5, t3, s3) {
      const i3 = /* @__PURE__ */ new Map();
      for (const n3 of e5.values())
        n3 instanceof Jt && i3.set(n3, new j(n3, t3, s3));
      return i3;
    }(this.graph.modulesById, this.outputOptions, c2), u2 = [], d2 = /* @__PURE__ */ new Map();
    for (const { alias: n3, modules: p3 } of i2 ? [{ alias: null, modules: l2 }] : r2 ? l2.map((e5) => ({ alias: null, modules: [e5] })) : Ua(this.graph.entryModules, o2, s2, this.inputOptions.onLog)) {
      p3.sort(Za);
      const s3 = new La(p3, this.inputOptions, this.outputOptions, this.unsetOptions, this.pluginDriver, this.graph.modulesById, d2, h2, this.facadeChunkByModule, this.includedNamespaces, n3, t2, e4, c2, a2);
      u2.push(s3);
    }
    for (const e5 of u2)
      e5.link();
    const p2 = [];
    for (const e5 of u2)
      p2.push(...e5.generateFacades());
    return [...u2, ...p2];
  }
}
function Yl(e4, t2, s2) {
  const i2 = s2.get(t2);
  if ("string" == typeof i2 && i2 !== e4)
    return Qe((n2 = t2.id, r2 = e4, o2 = i2, { code: ht, message: `Cannot assign "${V(n2)}" to the "${r2}" chunk as it is already in the "${o2}" chunk.` }));
  var n2, r2, o2;
  s2.set(t2, e4);
}
var Xl = [509, 0, 227, 0, 150, 4, 294, 9, 1368, 2, 2, 1, 6, 3, 41, 2, 5, 0, 166, 1, 574, 3, 9, 9, 370, 1, 81, 2, 71, 10, 50, 3, 123, 2, 54, 14, 32, 10, 3, 1, 11, 3, 46, 10, 8, 0, 46, 9, 7, 2, 37, 13, 2, 9, 6, 1, 45, 0, 13, 2, 49, 13, 9, 3, 2, 11, 83, 11, 7, 0, 3, 0, 158, 11, 6, 9, 7, 3, 56, 1, 2, 6, 3, 1, 3, 2, 10, 0, 11, 1, 3, 6, 4, 4, 193, 17, 10, 9, 5, 0, 82, 19, 13, 9, 214, 6, 3, 8, 28, 1, 83, 16, 16, 9, 82, 12, 9, 9, 84, 14, 5, 9, 243, 14, 166, 9, 71, 5, 2, 1, 3, 3, 2, 0, 2, 1, 13, 9, 120, 6, 3, 6, 4, 0, 29, 9, 41, 6, 2, 3, 9, 0, 10, 10, 47, 15, 406, 7, 2, 7, 17, 9, 57, 21, 2, 13, 123, 5, 4, 0, 2, 1, 2, 6, 2, 0, 9, 9, 49, 4, 2, 1, 2, 4, 9, 9, 330, 3, 10, 1, 2, 0, 49, 6, 4, 4, 14, 9, 5351, 0, 7, 14, 13835, 9, 87, 9, 39, 4, 60, 6, 26, 9, 1014, 0, 2, 54, 8, 3, 82, 0, 12, 1, 19628, 1, 4706, 45, 3, 22, 543, 4, 4, 5, 9, 7, 3, 6, 31, 3, 149, 2, 1418, 49, 513, 54, 5, 49, 9, 0, 15, 0, 23, 4, 2, 14, 1361, 6, 2, 16, 3, 6, 2, 1, 2, 4, 101, 0, 161, 6, 10, 9, 357, 0, 62, 13, 499, 13, 983, 6, 110, 6, 6, 9, 4759, 9, 787719, 239], Ql = [0, 11, 2, 25, 2, 18, 2, 1, 2, 14, 3, 13, 35, 122, 70, 52, 268, 28, 4, 48, 48, 31, 14, 29, 6, 37, 11, 29, 3, 35, 5, 7, 2, 4, 43, 157, 19, 35, 5, 35, 5, 39, 9, 51, 13, 10, 2, 14, 2, 6, 2, 1, 2, 10, 2, 14, 2, 6, 2, 1, 68, 310, 10, 21, 11, 7, 25, 5, 2, 41, 2, 8, 70, 5, 3, 0, 2, 43, 2, 1, 4, 0, 3, 22, 11, 22, 10, 30, 66, 18, 2, 1, 11, 21, 11, 25, 71, 55, 7, 1, 65, 0, 16, 3, 2, 2, 2, 28, 43, 28, 4, 28, 36, 7, 2, 27, 28, 53, 11, 21, 11, 18, 14, 17, 111, 72, 56, 50, 14, 50, 14, 35, 349, 41, 7, 1, 79, 28, 11, 0, 9, 21, 43, 17, 47, 20, 28, 22, 13, 52, 58, 1, 3, 0, 14, 44, 33, 24, 27, 35, 30, 0, 3, 0, 9, 34, 4, 0, 13, 47, 15, 3, 22, 0, 2, 0, 36, 17, 2, 24, 20, 1, 64, 6, 2, 0, 2, 3, 2, 14, 2, 9, 8, 46, 39, 7, 3, 1, 3, 21, 2, 6, 2, 1, 2, 4, 4, 0, 19, 0, 13, 4, 159, 52, 19, 3, 21, 2, 31, 47, 21, 1, 2, 0, 185, 46, 42, 3, 37, 47, 21, 0, 60, 42, 14, 0, 72, 26, 38, 6, 186, 43, 117, 63, 32, 7, 3, 0, 3, 7, 2, 1, 2, 23, 16, 0, 2, 0, 95, 7, 3, 38, 17, 0, 2, 0, 29, 0, 11, 39, 8, 0, 22, 0, 12, 45, 20, 0, 19, 72, 264, 8, 2, 36, 18, 0, 50, 29, 113, 6, 2, 1, 2, 37, 22, 0, 26, 5, 2, 1, 2, 31, 15, 0, 328, 18, 16, 0, 2, 12, 2, 33, 125, 0, 80, 921, 103, 110, 18, 195, 2637, 96, 16, 1071, 18, 5, 4026, 582, 8634, 568, 8, 30, 18, 78, 18, 29, 19, 47, 17, 3, 32, 20, 6, 18, 689, 63, 129, 74, 6, 0, 67, 12, 65, 1, 2, 0, 29, 6135, 9, 1237, 43, 8, 8936, 3, 2, 6, 2, 1, 2, 290, 16, 0, 30, 2, 3, 0, 15, 3, 9, 395, 2309, 106, 6, 12, 4, 8, 8, 9, 5991, 84, 2, 70, 2, 1, 3, 0, 3, 1, 3, 3, 2, 11, 2, 0, 2, 6, 2, 64, 2, 3, 3, 7, 2, 6, 2, 27, 2, 3, 2, 4, 2, 0, 4, 6, 2, 339, 3, 24, 2, 24, 2, 30, 2, 24, 2, 30, 2, 24, 2, 30, 2, 24, 2, 30, 2, 24, 2, 7, 1845, 30, 7, 5, 262, 61, 147, 44, 11, 6, 17, 0, 322, 29, 19, 43, 485, 27, 757, 6, 2, 3, 2, 1, 2, 14, 2, 196, 60, 67, 8, 0, 1205, 3, 2, 26, 2, 1, 2, 0, 3, 0, 2, 9, 2, 3, 2, 0, 2, 0, 7, 0, 5, 0, 2, 0, 2, 0, 2, 2, 2, 1, 2, 0, 3, 0, 2, 0, 2, 0, 2, 0, 2, 0, 2, 1, 2, 0, 3, 3, 2, 6, 2, 3, 2, 3, 2, 0, 2, 9, 2, 16, 6, 2, 2, 4, 2, 16, 4421, 42719, 33, 4153, 7, 221, 3, 5761, 15, 7472, 3104, 541, 1507, 4938, 6, 4191], Zl = "ªµºÀ-ÖØ-öø-ˁˆ-ˑˠ-ˤˬˮͰ-ʹͶͷͺ-ͽͿΆΈ-ΊΌΎ-ΡΣ-ϵϷ-ҁҊ-ԯԱ-Ֆՙՠ-ֈא-תׯ-ײؠ-يٮٯٱ-ۓەۥۦۮۯۺ-ۼۿܐܒ-ܯݍ-ޥޱߊ-ߪߴߵߺࠀ-ࠕࠚࠤࠨࡀ-ࡘࡠ-ࡪࡰ-ࢇࢉ-ࢎࢠ-ࣉऄ-हऽॐक़-ॡॱ-ঀঅ-ঌএঐও-নপ-রলশ-হঽৎড়ঢ়য়-ৡৰৱৼਅ-ਊਏਐਓ-ਨਪ-ਰਲਲ਼ਵਸ਼ਸਹਖ਼-ੜਫ਼ੲ-ੴઅ-ઍએ-ઑઓ-નપ-રલળવ-હઽૐૠૡૹଅ-ଌଏଐଓ-ନପ-ରଲଳଵ-ହଽଡ଼ଢ଼ୟ-ୡୱஃஅ-ஊஎ-ஐஒ-கஙசஜஞடணதந-பம-ஹௐఅ-ఌఎ-ఐఒ-నప-హఽౘ-ౚౝౠౡಀಅ-ಌಎ-ಐಒ-ನಪ-ಳವ-ಹಽೝೞೠೡೱೲഄ-ഌഎ-ഐഒ-ഺഽൎൔ-ൖൟ-ൡൺ-ൿඅ-ඖක-නඳ-රලව-ෆก-ะาำเ-ๆກຂຄຆ-ຊຌ-ຣລວ-ະາຳຽເ-ໄໆໜ-ໟༀཀ-ཇཉ-ཬྈ-ྌက-ဪဿၐ-ၕၚ-ၝၡၥၦၮ-ၰၵ-ႁႎႠ-ჅჇჍა-ჺჼ-ቈቊ-ቍቐ-ቖቘቚ-ቝበ-ኈኊ-ኍነ-ኰኲ-ኵኸ-ኾዀዂ-ዅወ-ዖዘ-ጐጒ-ጕጘ-ፚᎀ-ᎏᎠ-Ᏽᏸ-ᏽᐁ-ᙬᙯ-ᙿᚁ-ᚚᚠ-ᛪᛮ-ᛸᜀ-ᜑᜟ-ᜱᝀ-ᝑᝠ-ᝬᝮ-ᝰក-ឳៗៜᠠ-ᡸᢀ-ᢨᢪᢰ-ᣵᤀ-ᤞᥐ-ᥭᥰ-ᥴᦀ-ᦫᦰ-ᧉᨀ-ᨖᨠ-ᩔᪧᬅ-ᬳᭅ-ᭌᮃ-ᮠᮮᮯᮺ-ᯥᰀ-ᰣᱍ-ᱏᱚ-ᱽᲀ-ᲈᲐ-ᲺᲽ-Ჿᳩ-ᳬᳮ-ᳳᳵᳶᳺᴀ-ᶿḀ-ἕἘ-Ἕἠ-ὅὈ-Ὅὐ-ὗὙὛὝὟ-ώᾀ-ᾴᾶ-ᾼιῂ-ῄῆ-ῌῐ-ΐῖ-Ίῠ-Ῥῲ-ῴῶ-ῼⁱⁿₐ-ₜℂℇℊ-ℓℕ℘-ℝℤΩℨK-ℹℼ-ℿⅅ-ⅉⅎⅠ-ↈⰀ-ⳤⳫ-ⳮⳲⳳⴀ-ⴥⴧⴭⴰ-ⵧⵯⶀ-ⶖⶠ-ⶦⶨ-ⶮⶰ-ⶶⶸ-ⶾⷀ-ⷆⷈ-ⷎⷐ-ⷖⷘ-ⷞ々-〇〡-〩〱-〵〸-〼ぁ-ゖ゛-ゟァ-ヺー-ヿㄅ-ㄯㄱ-ㆎㆠ-ㆿㇰ-ㇿ㐀-䶿一-ꒌꓐ-ꓽꔀ-ꘌꘐ-ꘟꘪꘫꙀ-ꙮꙿ-ꚝꚠ-ꛯꜗ-ꜟꜢ-ꞈꞋ-ꟊꟐꟑꟓꟕ-ꟙꟲ-ꠁꠃ-ꠅꠇ-ꠊꠌ-ꠢꡀ-ꡳꢂ-ꢳꣲ-ꣷꣻꣽꣾꤊ-ꤥꤰ-ꥆꥠ-ꥼꦄ-ꦲꧏꧠ-ꧤꧦ-ꧯꧺ-ꧾꨀ-ꨨꩀ-ꩂꩄ-ꩋꩠ-ꩶꩺꩾ-ꪯꪱꪵꪶꪹ-ꪽꫀꫂꫛ-ꫝꫠ-ꫪꫲ-ꫴꬁ-ꬆꬉ-ꬎꬑ-ꬖꬠ-ꬦꬨ-ꬮꬰ-ꭚꭜ-ꭩꭰ-ꯢ가-힣ힰ-ퟆퟋ-ퟻ豈-舘並-龎ﬀ-ﬆﬓ-ﬗיִײַ-ﬨשׁ-זּטּ-לּמּנּסּףּפּצּ-ﮱﯓ-ﴽﵐ-ﶏﶒ-ﷇﷰ-ﷻﹰ-ﹴﹶ-ﻼＡ-Ｚａ-ｚｦ-ﾾￂ-ￇￊ-ￏￒ-ￗￚ-ￜ", Jl = { 3: "abstract boolean byte char class double enum export extends final float goto implements import int interface long native package private protected public short static super synchronized throws transient volatile", 5: "class enum extends super const export import", 6: "enum", strict: "implements interface let package private protected public static yield", strictBind: "eval arguments" }, ec = "break case catch continue debugger default do else finally for function if return switch throw try var while with null true false instanceof typeof void delete new in this", tc = { 5: ec, "5module": ec + " export import", 6: ec + " const class extends export import super" }, sc = /^in(stanceof)?$/, ic = new RegExp("[" + Zl + "]"), nc = new RegExp("[" + Zl + "‌‍·̀-ͯ·҃-֑҇-ׇֽֿׁׂׅׄؐ-ًؚ-٩ٰۖ-ۜ۟-۪ۤۧۨ-ۭ۰-۹ܑܰ-݊ަ-ް߀-߉߫-߽߳ࠖ-࠙ࠛ-ࠣࠥ-ࠧࠩ-࡙࠭-࡛࢘-࢟࣊-ࣣ࣡-ःऺ-़ा-ॏ॑-ॗॢॣ०-९ঁ-ঃ়া-ৄেৈো-্ৗৢৣ০-৯৾ਁ-ਃ਼ਾ-ੂੇੈੋ-੍ੑ੦-ੱੵઁ-ઃ઼ા-ૅે-ૉો-્ૢૣ૦-૯ૺ-૿ଁ-ଃ଼ା-ୄେୈୋ-୍୕-ୗୢୣ୦-୯ஂா-ூெ-ைொ-்ௗ௦-௯ఀ-ఄ఼ా-ౄె-ైొ-్ౕౖౢౣ౦-౯ಁ-ಃ಼ಾ-ೄೆ-ೈೊ-್ೕೖೢೣ೦-೯ೳഀ-ഃ഻഼ാ-ൄെ-ൈൊ-്ൗൢൣ൦-൯ඁ-ඃ්ා-ුූෘ-ෟ෦-෯ෲෳัิ-ฺ็-๎๐-๙ັິ-ຼ່-໎໐-໙༘༙༠-༩༹༵༷༾༿ཱ-྄྆྇ྍ-ྗྙ-ྼ࿆ါ-ှ၀-၉ၖ-ၙၞ-ၠၢ-ၤၧ-ၭၱ-ၴႂ-ႍႏ-ႝ፝-፟፩-፱ᜒ-᜕ᜲ-᜴ᝒᝓᝲᝳ឴-៓៝០-៩᠋-᠍᠏-᠙ᢩᤠ-ᤫᤰ-᤻᥆-᥏᧐-᧚ᨗ-ᨛᩕ-ᩞ᩠-᩿᩼-᪉᪐-᪙᪰-᪽ᪿ-ᫎᬀ-ᬄ᬴-᭄᭐-᭙᭫-᭳ᮀ-ᮂᮡ-ᮭ᮰-᮹᯦-᯳ᰤ-᰷᱀-᱉᱐-᱙᳐-᳔᳒-᳨᳭᳴᳷-᳹᷀-᷿‿⁀⁔⃐-⃥⃜⃡-⃰⳯-⵿⳱ⷠ-〪ⷿ-゙゚〯꘠-꘩꙯ꙴ-꙽ꚞꚟ꛰꛱ꠂ꠆ꠋꠣ-ꠧ꠬ꢀꢁꢴ-ꣅ꣐-꣙꣠-꣱ꣿ-꤉ꤦ-꤭ꥇ-꥓ꦀ-ꦃ꦳-꧀꧐-꧙ꧥ꧰-꧹ꨩ-ꨶꩃꩌꩍ꩐-꩙ꩻ-ꩽꪰꪲ-ꪴꪷꪸꪾ꪿꫁ꫫ-ꫯꫵ꫶ꯣ-ꯪ꯬꯭꯰-꯹ﬞ︀-️︠-︯︳︴﹍-﹏０-９＿]");
function rc(e4, t2) {
  for (var s2 = 65536, i2 = 0; i2 < t2.length; i2 += 2) {
    if ((s2 += t2[i2]) > e4)
      return false;
    if ((s2 += t2[i2 + 1]) >= e4)
      return true;
  }
  return false;
}
function oc(e4, t2) {
  return e4 < 65 ? 36 === e4 : e4 < 91 || (e4 < 97 ? 95 === e4 : e4 < 123 || (e4 <= 65535 ? e4 >= 170 && ic.test(String.fromCharCode(e4)) : false !== t2 && rc(e4, Ql)));
}
function ac(e4, t2) {
  return e4 < 48 ? 36 === e4 : e4 < 58 || !(e4 < 65) && (e4 < 91 || (e4 < 97 ? 95 === e4 : e4 < 123 || (e4 <= 65535 ? e4 >= 170 && nc.test(String.fromCharCode(e4)) : false !== t2 && (rc(e4, Ql) || rc(e4, Xl)))));
}
var lc = function(e4, t2) {
  void 0 === t2 && (t2 = {}), this.label = e4, this.keyword = t2.keyword, this.beforeExpr = !!t2.beforeExpr, this.startsExpr = !!t2.startsExpr, this.isLoop = !!t2.isLoop, this.isAssign = !!t2.isAssign, this.prefix = !!t2.prefix, this.postfix = !!t2.postfix, this.binop = t2.binop || null, this.updateContext = null;
};
function cc(e4, t2) {
  return new lc(e4, { beforeExpr: true, binop: t2 });
}
var hc = { beforeExpr: true }, uc = { startsExpr: true }, dc = {};
function pc(e4, t2) {
  return void 0 === t2 && (t2 = {}), t2.keyword = e4, dc[e4] = new lc(e4, t2);
}
var fc = { num: new lc("num", uc), regexp: new lc("regexp", uc), string: new lc("string", uc), name: new lc("name", uc), privateId: new lc("privateId", uc), eof: new lc("eof"), bracketL: new lc("[", { beforeExpr: true, startsExpr: true }), bracketR: new lc("]"), braceL: new lc("{", { beforeExpr: true, startsExpr: true }), braceR: new lc("}"), parenL: new lc("(", { beforeExpr: true, startsExpr: true }), parenR: new lc(")"), comma: new lc(",", hc), semi: new lc(";", hc), colon: new lc(":", hc), dot: new lc("."), question: new lc("?", hc), questionDot: new lc("?."), arrow: new lc("=>", hc), template: new lc("template"), invalidTemplate: new lc("invalidTemplate"), ellipsis: new lc("...", hc), backQuote: new lc("`", uc), dollarBraceL: new lc("${", { beforeExpr: true, startsExpr: true }), eq: new lc("=", { beforeExpr: true, isAssign: true }), assign: new lc("_=", { beforeExpr: true, isAssign: true }), incDec: new lc("++/--", { prefix: true, postfix: true, startsExpr: true }), prefix: new lc("!/~", { beforeExpr: true, prefix: true, startsExpr: true }), logicalOR: cc("||", 1), logicalAND: cc("&&", 2), bitwiseOR: cc("|", 3), bitwiseXOR: cc("^", 4), bitwiseAND: cc("&", 5), equality: cc("==/!=/===/!==", 6), relational: cc("</>/<=/>=", 7), bitShift: cc("<</>>/>>>", 8), plusMin: new lc("+/-", { beforeExpr: true, binop: 9, prefix: true, startsExpr: true }), modulo: cc("%", 10), star: cc("*", 10), slash: cc("/", 10), starstar: new lc("**", { beforeExpr: true }), coalesce: cc("??", 1), _break: pc("break"), _case: pc("case", hc), _catch: pc("catch"), _continue: pc("continue"), _debugger: pc("debugger"), _default: pc("default", hc), _do: pc("do", { isLoop: true, beforeExpr: true }), _else: pc("else", hc), _finally: pc("finally"), _for: pc("for", { isLoop: true }), _function: pc("function", uc), _if: pc("if"), _return: pc("return", hc), _switch: pc("switch"), _throw: pc("throw", hc), _try: pc("try"), _var: pc("var"), _const: pc("const"), _while: pc("while", { isLoop: true }), _with: pc("with"), _new: pc("new", { beforeExpr: true, startsExpr: true }), _this: pc("this", uc), _super: pc("super", uc), _class: pc("class", uc), _extends: pc("extends", hc), _export: pc("export"), _import: pc("import", uc), _null: pc("null", uc), _true: pc("true", uc), _false: pc("false", uc), _in: pc("in", { beforeExpr: true, binop: 7 }), _instanceof: pc("instanceof", { beforeExpr: true, binop: 7 }), _typeof: pc("typeof", { beforeExpr: true, prefix: true, startsExpr: true }), _void: pc("void", { beforeExpr: true, prefix: true, startsExpr: true }), _delete: pc("delete", { beforeExpr: true, prefix: true, startsExpr: true }) }, mc = /\r\n?|\n|\u2028|\u2029/, gc = new RegExp(mc.source, "g");
function yc(e4) {
  return 10 === e4 || 13 === e4 || 8232 === e4 || 8233 === e4;
}
function xc(e4, t2, s2) {
  void 0 === s2 && (s2 = e4.length);
  for (var i2 = t2; i2 < s2; i2++) {
    var n2 = e4.charCodeAt(i2);
    if (yc(n2))
      return i2 < s2 - 1 && 13 === n2 && 10 === e4.charCodeAt(i2 + 1) ? i2 + 2 : i2 + 1;
  }
  return -1;
}
var Ec = /[\u1680\u2000-\u200a\u202f\u205f\u3000\ufeff]/, bc = /(?:\s|\/\/.*|\/\*[^]*?\*\/)*/g, vc = Object.prototype, Sc = vc.hasOwnProperty, Ac = vc.toString, kc = Object.hasOwn || function(e4, t2) {
  return Sc.call(e4, t2);
}, Ic = Array.isArray || function(e4) {
  return "[object Array]" === Ac.call(e4);
};
function wc(e4) {
  return new RegExp("^(?:" + e4.replace(/ /g, "|") + ")$");
}
function Pc(e4) {
  return e4 <= 65535 ? String.fromCharCode(e4) : (e4 -= 65536, String.fromCharCode(55296 + (e4 >> 10), 56320 + (1023 & e4)));
}
var Cc = /(?:[\uD800-\uDBFF](?![\uDC00-\uDFFF])|(?:[^\uD800-\uDBFF]|^)[\uDC00-\uDFFF])/, $c = function(e4, t2) {
  this.line = e4, this.column = t2;
};
$c.prototype.offset = function(e4) {
  return new $c(this.line, this.column + e4);
};
var Nc = function(e4, t2, s2) {
  this.start = t2, this.end = s2, null !== e4.sourceFile && (this.source = e4.sourceFile);
};
function _c(e4, t2) {
  for (var s2 = 1, i2 = 0; ; ) {
    var n2 = xc(e4, i2, t2);
    if (n2 < 0)
      return new $c(s2, t2 - i2);
    ++s2, i2 = n2;
  }
}
var Rc = { ecmaVersion: null, sourceType: "script", onInsertedSemicolon: null, onTrailingComma: null, allowReserved: null, allowReturnOutsideFunction: false, allowImportExportEverywhere: false, allowAwaitOutsideFunction: null, allowSuperOutsideMethod: null, allowHashBang: false, checkPrivateFields: true, locations: false, onToken: null, onComment: null, ranges: false, program: null, sourceFile: null, directSourceFile: null, preserveParens: false }, Mc = false;
function Oc(e4) {
  var t2 = {};
  for (var s2 in Rc)
    t2[s2] = e4 && kc(e4, s2) ? e4[s2] : Rc[s2];
  if ("latest" === t2.ecmaVersion ? t2.ecmaVersion = 1e8 : null == t2.ecmaVersion ? (!Mc && "object" == typeof console && console.warn && (Mc = true, console.warn("Since Acorn 8.0.0, options.ecmaVersion is required.\nDefaulting to 2020, but this will stop working in the future.")), t2.ecmaVersion = 11) : t2.ecmaVersion >= 2015 && (t2.ecmaVersion -= 2009), null == t2.allowReserved && (t2.allowReserved = t2.ecmaVersion < 5), e4 && null != e4.allowHashBang || (t2.allowHashBang = t2.ecmaVersion >= 14), Ic(t2.onToken)) {
    var i2 = t2.onToken;
    t2.onToken = function(e5) {
      return i2.push(e5);
    };
  }
  return Ic(t2.onComment) && (t2.onComment = function(e5, t3) {
    return function(s3, i3, n2, r2, o2, a2) {
      var l2 = { type: s3 ? "Block" : "Line", value: i3, start: n2, end: r2 };
      e5.locations && (l2.loc = new Nc(this, o2, a2)), e5.ranges && (l2.range = [n2, r2]), t3.push(l2);
    };
  }(t2, t2.onComment)), t2;
}
var Dc = 256;
function Lc(e4, t2) {
  return 2 | (e4 ? 4 : 0) | (t2 ? 8 : 0);
}
var Tc = function(e4, t2, s2) {
  this.options = e4 = Oc(e4), this.sourceFile = e4.sourceFile, this.keywords = wc(tc[e4.ecmaVersion >= 6 ? 6 : "module" === e4.sourceType ? "5module" : 5]);
  var i2 = "";
  true !== e4.allowReserved && (i2 = Jl[e4.ecmaVersion >= 6 ? 6 : 5 === e4.ecmaVersion ? 5 : 3], "module" === e4.sourceType && (i2 += " await")), this.reservedWords = wc(i2);
  var n2 = (i2 ? i2 + " " : "") + Jl.strict;
  this.reservedWordsStrict = wc(n2), this.reservedWordsStrictBind = wc(n2 + " " + Jl.strictBind), this.input = String(t2), this.containsEsc = false, s2 ? (this.pos = s2, this.lineStart = this.input.lastIndexOf("\n", s2 - 1) + 1, this.curLine = this.input.slice(0, this.lineStart).split(mc).length) : (this.pos = this.lineStart = 0, this.curLine = 1), this.type = fc.eof, this.value = null, this.start = this.end = this.pos, this.startLoc = this.endLoc = this.curPosition(), this.lastTokEndLoc = this.lastTokStartLoc = null, this.lastTokStart = this.lastTokEnd = this.pos, this.context = this.initialContext(), this.exprAllowed = true, this.inModule = "module" === e4.sourceType, this.strict = this.inModule || this.strictDirective(this.pos), this.potentialArrowAt = -1, this.potentialArrowInForAwait = false, this.yieldPos = this.awaitPos = this.awaitIdentPos = 0, this.labels = [], this.undefinedExports = /* @__PURE__ */ Object.create(null), 0 === this.pos && e4.allowHashBang && "#!" === this.input.slice(0, 2) && this.skipLineComment(2), this.scopeStack = [], this.enterScope(1), this.regexpState = null, this.privateNameStack = [];
}, Vc = { inFunction: { configurable: true }, inGenerator: { configurable: true }, inAsync: { configurable: true }, canAwait: { configurable: true }, allowSuper: { configurable: true }, allowDirectSuper: { configurable: true }, treatFunctionsAsVar: { configurable: true }, allowNewDotTarget: { configurable: true }, inClassStaticBlock: { configurable: true } };
Tc.prototype.parse = function() {
  var e4 = this.options.program || this.startNode();
  return this.nextToken(), this.parseTopLevel(e4);
}, Vc.inFunction.get = function() {
  return (2 & this.currentVarScope().flags) > 0;
}, Vc.inGenerator.get = function() {
  return (8 & this.currentVarScope().flags) > 0 && !this.currentVarScope().inClassFieldInit;
}, Vc.inAsync.get = function() {
  return (4 & this.currentVarScope().flags) > 0 && !this.currentVarScope().inClassFieldInit;
}, Vc.canAwait.get = function() {
  for (var e4 = this.scopeStack.length - 1; e4 >= 0; e4--) {
    var t2 = this.scopeStack[e4];
    if (t2.inClassFieldInit || t2.flags & Dc)
      return false;
    if (2 & t2.flags)
      return (4 & t2.flags) > 0;
  }
  return this.inModule && this.options.ecmaVersion >= 13 || this.options.allowAwaitOutsideFunction;
}, Vc.allowSuper.get = function() {
  var e4 = this.currentThisScope(), t2 = e4.flags, s2 = e4.inClassFieldInit;
  return (64 & t2) > 0 || s2 || this.options.allowSuperOutsideMethod;
}, Vc.allowDirectSuper.get = function() {
  return (128 & this.currentThisScope().flags) > 0;
}, Vc.treatFunctionsAsVar.get = function() {
  return this.treatFunctionsAsVarInScope(this.currentScope());
}, Vc.allowNewDotTarget.get = function() {
  var e4 = this.currentThisScope(), t2 = e4.flags, s2 = e4.inClassFieldInit;
  return (258 & t2) > 0 || s2;
}, Vc.inClassStaticBlock.get = function() {
  return (this.currentVarScope().flags & Dc) > 0;
}, Tc.extend = function() {
  for (var e4 = [], t2 = arguments.length; t2--; )
    e4[t2] = arguments[t2];
  for (var s2 = this, i2 = 0; i2 < e4.length; i2++)
    s2 = e4[i2](s2);
  return s2;
}, Tc.parse = function(e4, t2) {
  return new this(t2, e4).parse();
}, Tc.parseExpressionAt = function(e4, t2, s2) {
  var i2 = new this(s2, e4, t2);
  return i2.nextToken(), i2.parseExpression();
}, Tc.tokenizer = function(e4, t2) {
  return new this(t2, e4);
}, Object.defineProperties(Tc.prototype, Vc);
var Bc = Tc.prototype, zc = /^(?:'((?:\\.|[^'\\])*?)'|"((?:\\.|[^"\\])*?)")/;
Bc.strictDirective = function(e4) {
  if (this.options.ecmaVersion < 5)
    return false;
  for (; ; ) {
    bc.lastIndex = e4, e4 += bc.exec(this.input)[0].length;
    var t2 = zc.exec(this.input.slice(e4));
    if (!t2)
      return false;
    if ("use strict" === (t2[1] || t2[2])) {
      bc.lastIndex = e4 + t2[0].length;
      var s2 = bc.exec(this.input), i2 = s2.index + s2[0].length, n2 = this.input.charAt(i2);
      return ";" === n2 || "}" === n2 || mc.test(s2[0]) && !(/[(`.[+\-/*%<>=,?^&]/.test(n2) || "!" === n2 && "=" === this.input.charAt(i2 + 1));
    }
    e4 += t2[0].length, bc.lastIndex = e4, e4 += bc.exec(this.input)[0].length, ";" === this.input[e4] && e4++;
  }
}, Bc.eat = function(e4) {
  return this.type === e4 && (this.next(), true);
}, Bc.isContextual = function(e4) {
  return this.type === fc.name && this.value === e4 && !this.containsEsc;
}, Bc.eatContextual = function(e4) {
  return !!this.isContextual(e4) && (this.next(), true);
}, Bc.expectContextual = function(e4) {
  this.eatContextual(e4) || this.unexpected();
}, Bc.canInsertSemicolon = function() {
  return this.type === fc.eof || this.type === fc.braceR || mc.test(this.input.slice(this.lastTokEnd, this.start));
}, Bc.insertSemicolon = function() {
  if (this.canInsertSemicolon())
    return this.options.onInsertedSemicolon && this.options.onInsertedSemicolon(this.lastTokEnd, this.lastTokEndLoc), true;
}, Bc.semicolon = function() {
  this.eat(fc.semi) || this.insertSemicolon() || this.unexpected();
}, Bc.afterTrailingComma = function(e4, t2) {
  if (this.type === e4)
    return this.options.onTrailingComma && this.options.onTrailingComma(this.lastTokStart, this.lastTokStartLoc), t2 || this.next(), true;
}, Bc.expect = function(e4) {
  this.eat(e4) || this.unexpected();
}, Bc.unexpected = function(e4) {
  this.raise(null != e4 ? e4 : this.start, "Unexpected token");
};
var Fc = function() {
  this.shorthandAssign = this.trailingComma = this.parenthesizedAssign = this.parenthesizedBind = this.doubleProto = -1;
};
Bc.checkPatternErrors = function(e4, t2) {
  if (e4) {
    e4.trailingComma > -1 && this.raiseRecoverable(e4.trailingComma, "Comma is not permitted after the rest element");
    var s2 = t2 ? e4.parenthesizedAssign : e4.parenthesizedBind;
    s2 > -1 && this.raiseRecoverable(s2, t2 ? "Assigning to rvalue" : "Parenthesized pattern");
  }
}, Bc.checkExpressionErrors = function(e4, t2) {
  if (!e4)
    return false;
  var s2 = e4.shorthandAssign, i2 = e4.doubleProto;
  if (!t2)
    return s2 >= 0 || i2 >= 0;
  s2 >= 0 && this.raise(s2, "Shorthand property assignments are valid only in destructuring patterns"), i2 >= 0 && this.raiseRecoverable(i2, "Redefinition of __proto__ property");
}, Bc.checkYieldAwaitInDefaultParams = function() {
  this.yieldPos && (!this.awaitPos || this.yieldPos < this.awaitPos) && this.raise(this.yieldPos, "Yield expression cannot be a default value"), this.awaitPos && this.raise(this.awaitPos, "Await expression cannot be a default value");
}, Bc.isSimpleAssignTarget = function(e4) {
  return "ParenthesizedExpression" === e4.type ? this.isSimpleAssignTarget(e4.expression) : "Identifier" === e4.type || "MemberExpression" === e4.type;
};
var jc = Tc.prototype;
jc.parseTopLevel = function(e4) {
  var t2 = /* @__PURE__ */ Object.create(null);
  for (e4.body || (e4.body = []); this.type !== fc.eof; ) {
    var s2 = this.parseStatement(null, true, t2);
    e4.body.push(s2);
  }
  if (this.inModule)
    for (var i2 = 0, n2 = Object.keys(this.undefinedExports); i2 < n2.length; i2 += 1) {
      var r2 = n2[i2];
      this.raiseRecoverable(this.undefinedExports[r2].start, "Export '" + r2 + "' is not defined");
    }
  return this.adaptDirectivePrologue(e4.body), this.next(), e4.sourceType = this.options.sourceType, this.finishNode(e4, "Program");
};
var Uc = { kind: "loop" }, Gc = { kind: "switch" };
jc.isLet = function(e4) {
  if (this.options.ecmaVersion < 6 || !this.isContextual("let"))
    return false;
  bc.lastIndex = this.pos;
  var t2 = bc.exec(this.input), s2 = this.pos + t2[0].length, i2 = this.input.charCodeAt(s2);
  if (91 === i2 || 92 === i2)
    return true;
  if (e4)
    return false;
  if (123 === i2 || i2 > 55295 && i2 < 56320)
    return true;
  if (oc(i2, true)) {
    for (var n2 = s2 + 1; ac(i2 = this.input.charCodeAt(n2), true); )
      ++n2;
    if (92 === i2 || i2 > 55295 && i2 < 56320)
      return true;
    var r2 = this.input.slice(s2, n2);
    if (!sc.test(r2))
      return true;
  }
  return false;
}, jc.isAsyncFunction = function() {
  if (this.options.ecmaVersion < 8 || !this.isContextual("async"))
    return false;
  bc.lastIndex = this.pos;
  var e4, t2 = bc.exec(this.input), s2 = this.pos + t2[0].length;
  return !(mc.test(this.input.slice(this.pos, s2)) || "function" !== this.input.slice(s2, s2 + 8) || s2 + 8 !== this.input.length && (ac(e4 = this.input.charCodeAt(s2 + 8)) || e4 > 55295 && e4 < 56320));
}, jc.parseStatement = function(e4, t2, s2) {
  var i2, n2 = this.type, r2 = this.startNode();
  switch (this.isLet(e4) && (n2 = fc._var, i2 = "let"), n2) {
    case fc._break:
    case fc._continue:
      return this.parseBreakContinueStatement(r2, n2.keyword);
    case fc._debugger:
      return this.parseDebuggerStatement(r2);
    case fc._do:
      return this.parseDoStatement(r2);
    case fc._for:
      return this.parseForStatement(r2);
    case fc._function:
      return e4 && (this.strict || "if" !== e4 && "label" !== e4) && this.options.ecmaVersion >= 6 && this.unexpected(), this.parseFunctionStatement(r2, false, !e4);
    case fc._class:
      return e4 && this.unexpected(), this.parseClass(r2, true);
    case fc._if:
      return this.parseIfStatement(r2);
    case fc._return:
      return this.parseReturnStatement(r2);
    case fc._switch:
      return this.parseSwitchStatement(r2);
    case fc._throw:
      return this.parseThrowStatement(r2);
    case fc._try:
      return this.parseTryStatement(r2);
    case fc._const:
    case fc._var:
      return i2 = i2 || this.value, e4 && "var" !== i2 && this.unexpected(), this.parseVarStatement(r2, i2);
    case fc._while:
      return this.parseWhileStatement(r2);
    case fc._with:
      return this.parseWithStatement(r2);
    case fc.braceL:
      return this.parseBlock(true, r2);
    case fc.semi:
      return this.parseEmptyStatement(r2);
    case fc._export:
    case fc._import:
      if (this.options.ecmaVersion > 10 && n2 === fc._import) {
        bc.lastIndex = this.pos;
        var o2 = bc.exec(this.input), a2 = this.pos + o2[0].length, l2 = this.input.charCodeAt(a2);
        if (40 === l2 || 46 === l2)
          return this.parseExpressionStatement(r2, this.parseExpression());
      }
      return this.options.allowImportExportEverywhere || (t2 || this.raise(this.start, "'import' and 'export' may only appear at the top level"), this.inModule || this.raise(this.start, "'import' and 'export' may appear only with 'sourceType: module'")), n2 === fc._import ? this.parseImport(r2) : this.parseExport(r2, s2);
    default:
      if (this.isAsyncFunction())
        return e4 && this.unexpected(), this.next(), this.parseFunctionStatement(r2, true, !e4);
      var c2 = this.value, h2 = this.parseExpression();
      return n2 === fc.name && "Identifier" === h2.type && this.eat(fc.colon) ? this.parseLabeledStatement(r2, c2, h2, e4) : this.parseExpressionStatement(r2, h2);
  }
}, jc.parseBreakContinueStatement = function(e4, t2) {
  var s2 = "break" === t2;
  this.next(), this.eat(fc.semi) || this.insertSemicolon() ? e4.label = null : this.type !== fc.name ? this.unexpected() : (e4.label = this.parseIdent(), this.semicolon());
  for (var i2 = 0; i2 < this.labels.length; ++i2) {
    var n2 = this.labels[i2];
    if (null == e4.label || n2.name === e4.label.name) {
      if (null != n2.kind && (s2 || "loop" === n2.kind))
        break;
      if (e4.label && s2)
        break;
    }
  }
  return i2 === this.labels.length && this.raise(e4.start, "Unsyntactic " + t2), this.finishNode(e4, s2 ? "BreakStatement" : "ContinueStatement");
}, jc.parseDebuggerStatement = function(e4) {
  return this.next(), this.semicolon(), this.finishNode(e4, "DebuggerStatement");
}, jc.parseDoStatement = function(e4) {
  return this.next(), this.labels.push(Uc), e4.body = this.parseStatement("do"), this.labels.pop(), this.expect(fc._while), e4.test = this.parseParenExpression(), this.options.ecmaVersion >= 6 ? this.eat(fc.semi) : this.semicolon(), this.finishNode(e4, "DoWhileStatement");
}, jc.parseForStatement = function(e4) {
  this.next();
  var t2 = this.options.ecmaVersion >= 9 && this.canAwait && this.eatContextual("await") ? this.lastTokStart : -1;
  if (this.labels.push(Uc), this.enterScope(0), this.expect(fc.parenL), this.type === fc.semi)
    return t2 > -1 && this.unexpected(t2), this.parseFor(e4, null);
  var s2 = this.isLet();
  if (this.type === fc._var || this.type === fc._const || s2) {
    var i2 = this.startNode(), n2 = s2 ? "let" : this.value;
    return this.next(), this.parseVar(i2, true, n2), this.finishNode(i2, "VariableDeclaration"), (this.type === fc._in || this.options.ecmaVersion >= 6 && this.isContextual("of")) && 1 === i2.declarations.length ? (this.options.ecmaVersion >= 9 && (this.type === fc._in ? t2 > -1 && this.unexpected(t2) : e4.await = t2 > -1), this.parseForIn(e4, i2)) : (t2 > -1 && this.unexpected(t2), this.parseFor(e4, i2));
  }
  var r2 = this.isContextual("let"), o2 = false, a2 = new Fc(), l2 = this.parseExpression(!(t2 > -1) || "await", a2);
  return this.type === fc._in || (o2 = this.options.ecmaVersion >= 6 && this.isContextual("of")) ? (this.options.ecmaVersion >= 9 && (this.type === fc._in ? t2 > -1 && this.unexpected(t2) : e4.await = t2 > -1), r2 && o2 && this.raise(l2.start, "The left-hand side of a for-of loop may not start with 'let'."), this.toAssignable(l2, false, a2), this.checkLValPattern(l2), this.parseForIn(e4, l2)) : (this.checkExpressionErrors(a2, true), t2 > -1 && this.unexpected(t2), this.parseFor(e4, l2));
}, jc.parseFunctionStatement = function(e4, t2, s2) {
  return this.next(), this.parseFunction(e4, qc | (s2 ? 0 : Hc), false, t2);
}, jc.parseIfStatement = function(e4) {
  return this.next(), e4.test = this.parseParenExpression(), e4.consequent = this.parseStatement("if"), e4.alternate = this.eat(fc._else) ? this.parseStatement("if") : null, this.finishNode(e4, "IfStatement");
}, jc.parseReturnStatement = function(e4) {
  return this.inFunction || this.options.allowReturnOutsideFunction || this.raise(this.start, "'return' outside of function"), this.next(), this.eat(fc.semi) || this.insertSemicolon() ? e4.argument = null : (e4.argument = this.parseExpression(), this.semicolon()), this.finishNode(e4, "ReturnStatement");
}, jc.parseSwitchStatement = function(e4) {
  var t2;
  this.next(), e4.discriminant = this.parseParenExpression(), e4.cases = [], this.expect(fc.braceL), this.labels.push(Gc), this.enterScope(0);
  for (var s2 = false; this.type !== fc.braceR; )
    if (this.type === fc._case || this.type === fc._default) {
      var i2 = this.type === fc._case;
      t2 && this.finishNode(t2, "SwitchCase"), e4.cases.push(t2 = this.startNode()), t2.consequent = [], this.next(), i2 ? t2.test = this.parseExpression() : (s2 && this.raiseRecoverable(this.lastTokStart, "Multiple default clauses"), s2 = true, t2.test = null), this.expect(fc.colon);
    } else
      t2 || this.unexpected(), t2.consequent.push(this.parseStatement(null));
  return this.exitScope(), t2 && this.finishNode(t2, "SwitchCase"), this.next(), this.labels.pop(), this.finishNode(e4, "SwitchStatement");
}, jc.parseThrowStatement = function(e4) {
  return this.next(), mc.test(this.input.slice(this.lastTokEnd, this.start)) && this.raise(this.lastTokEnd, "Illegal newline after throw"), e4.argument = this.parseExpression(), this.semicolon(), this.finishNode(e4, "ThrowStatement");
};
var Wc = [];
jc.parseCatchClauseParam = function() {
  var e4 = this.parseBindingAtom(), t2 = "Identifier" === e4.type;
  return this.enterScope(t2 ? 32 : 0), this.checkLValPattern(e4, t2 ? 4 : 2), this.expect(fc.parenR), e4;
}, jc.parseTryStatement = function(e4) {
  if (this.next(), e4.block = this.parseBlock(), e4.handler = null, this.type === fc._catch) {
    var t2 = this.startNode();
    this.next(), this.eat(fc.parenL) ? t2.param = this.parseCatchClauseParam() : (this.options.ecmaVersion < 10 && this.unexpected(), t2.param = null, this.enterScope(0)), t2.body = this.parseBlock(false), this.exitScope(), e4.handler = this.finishNode(t2, "CatchClause");
  }
  return e4.finalizer = this.eat(fc._finally) ? this.parseBlock() : null, e4.handler || e4.finalizer || this.raise(e4.start, "Missing catch or finally clause"), this.finishNode(e4, "TryStatement");
}, jc.parseVarStatement = function(e4, t2, s2) {
  return this.next(), this.parseVar(e4, false, t2, s2), this.semicolon(), this.finishNode(e4, "VariableDeclaration");
}, jc.parseWhileStatement = function(e4) {
  return this.next(), e4.test = this.parseParenExpression(), this.labels.push(Uc), e4.body = this.parseStatement("while"), this.labels.pop(), this.finishNode(e4, "WhileStatement");
}, jc.parseWithStatement = function(e4) {
  return this.strict && this.raise(this.start, "'with' in strict mode"), this.next(), e4.object = this.parseParenExpression(), e4.body = this.parseStatement("with"), this.finishNode(e4, "WithStatement");
}, jc.parseEmptyStatement = function(e4) {
  return this.next(), this.finishNode(e4, "EmptyStatement");
}, jc.parseLabeledStatement = function(e4, t2, s2, i2) {
  for (var n2 = 0, r2 = this.labels; n2 < r2.length; n2 += 1) {
    r2[n2].name === t2 && this.raise(s2.start, "Label '" + t2 + "' is already declared");
  }
  for (var o2 = this.type.isLoop ? "loop" : this.type === fc._switch ? "switch" : null, a2 = this.labels.length - 1; a2 >= 0; a2--) {
    var l2 = this.labels[a2];
    if (l2.statementStart !== e4.start)
      break;
    l2.statementStart = this.start, l2.kind = o2;
  }
  return this.labels.push({ name: t2, kind: o2, statementStart: this.start }), e4.body = this.parseStatement(i2 ? -1 === i2.indexOf("label") ? i2 + "label" : i2 : "label"), this.labels.pop(), e4.label = s2, this.finishNode(e4, "LabeledStatement");
}, jc.parseExpressionStatement = function(e4, t2) {
  return e4.expression = t2, this.semicolon(), this.finishNode(e4, "ExpressionStatement");
}, jc.parseBlock = function(e4, t2, s2) {
  for (void 0 === e4 && (e4 = true), void 0 === t2 && (t2 = this.startNode()), t2.body = [], this.expect(fc.braceL), e4 && this.enterScope(0); this.type !== fc.braceR; ) {
    var i2 = this.parseStatement(null);
    t2.body.push(i2);
  }
  return s2 && (this.strict = false), this.next(), e4 && this.exitScope(), this.finishNode(t2, "BlockStatement");
}, jc.parseFor = function(e4, t2) {
  return e4.init = t2, this.expect(fc.semi), e4.test = this.type === fc.semi ? null : this.parseExpression(), this.expect(fc.semi), e4.update = this.type === fc.parenR ? null : this.parseExpression(), this.expect(fc.parenR), e4.body = this.parseStatement("for"), this.exitScope(), this.labels.pop(), this.finishNode(e4, "ForStatement");
}, jc.parseForIn = function(e4, t2) {
  var s2 = this.type === fc._in;
  return this.next(), "VariableDeclaration" === t2.type && null != t2.declarations[0].init && (!s2 || this.options.ecmaVersion < 8 || this.strict || "var" !== t2.kind || "Identifier" !== t2.declarations[0].id.type) && this.raise(t2.start, (s2 ? "for-in" : "for-of") + " loop variable declaration may not have an initializer"), e4.left = t2, e4.right = s2 ? this.parseExpression() : this.parseMaybeAssign(), this.expect(fc.parenR), e4.body = this.parseStatement("for"), this.exitScope(), this.labels.pop(), this.finishNode(e4, s2 ? "ForInStatement" : "ForOfStatement");
}, jc.parseVar = function(e4, t2, s2, i2) {
  for (e4.declarations = [], e4.kind = s2; ; ) {
    var n2 = this.startNode();
    if (this.parseVarId(n2, s2), this.eat(fc.eq) ? n2.init = this.parseMaybeAssign(t2) : i2 || "const" !== s2 || this.type === fc._in || this.options.ecmaVersion >= 6 && this.isContextual("of") ? i2 || "Identifier" === n2.id.type || t2 && (this.type === fc._in || this.isContextual("of")) ? n2.init = null : this.raise(this.lastTokEnd, "Complex binding patterns require an initialization value") : this.unexpected(), e4.declarations.push(this.finishNode(n2, "VariableDeclarator")), !this.eat(fc.comma))
      break;
  }
  return e4;
}, jc.parseVarId = function(e4, t2) {
  e4.id = this.parseBindingAtom(), this.checkLValPattern(e4.id, "var" === t2 ? 1 : 2, false);
};
var qc = 1, Hc = 2;
function Kc(e4, t2) {
  var s2 = t2.key.name, i2 = e4[s2], n2 = "true";
  return "MethodDefinition" !== t2.type || "get" !== t2.kind && "set" !== t2.kind || (n2 = (t2.static ? "s" : "i") + t2.kind), "iget" === i2 && "iset" === n2 || "iset" === i2 && "iget" === n2 || "sget" === i2 && "sset" === n2 || "sset" === i2 && "sget" === n2 ? (e4[s2] = "true", false) : !!i2 || (e4[s2] = n2, false);
}
function Yc(e4, t2) {
  var s2 = e4.computed, i2 = e4.key;
  return !s2 && ("Identifier" === i2.type && i2.name === t2 || "Literal" === i2.type && i2.value === t2);
}
jc.parseFunction = function(e4, t2, s2, i2, n2) {
  this.initFunction(e4), (this.options.ecmaVersion >= 9 || this.options.ecmaVersion >= 6 && !i2) && (this.type === fc.star && t2 & Hc && this.unexpected(), e4.generator = this.eat(fc.star)), this.options.ecmaVersion >= 8 && (e4.async = !!i2), t2 & qc && (e4.id = 4 & t2 && this.type !== fc.name ? null : this.parseIdent(), !e4.id || t2 & Hc || this.checkLValSimple(e4.id, this.strict || e4.generator || e4.async ? this.treatFunctionsAsVar ? 1 : 2 : 3));
  var r2 = this.yieldPos, o2 = this.awaitPos, a2 = this.awaitIdentPos;
  return this.yieldPos = 0, this.awaitPos = 0, this.awaitIdentPos = 0, this.enterScope(Lc(e4.async, e4.generator)), t2 & qc || (e4.id = this.type === fc.name ? this.parseIdent() : null), this.parseFunctionParams(e4), this.parseFunctionBody(e4, s2, false, n2), this.yieldPos = r2, this.awaitPos = o2, this.awaitIdentPos = a2, this.finishNode(e4, t2 & qc ? "FunctionDeclaration" : "FunctionExpression");
}, jc.parseFunctionParams = function(e4) {
  this.expect(fc.parenL), e4.params = this.parseBindingList(fc.parenR, false, this.options.ecmaVersion >= 8), this.checkYieldAwaitInDefaultParams();
}, jc.parseClass = function(e4, t2) {
  this.next();
  var s2 = this.strict;
  this.strict = true, this.parseClassId(e4, t2), this.parseClassSuper(e4);
  var i2 = this.enterClassBody(), n2 = this.startNode(), r2 = false;
  for (n2.body = [], this.expect(fc.braceL); this.type !== fc.braceR; ) {
    var o2 = this.parseClassElement(null !== e4.superClass);
    o2 && (n2.body.push(o2), "MethodDefinition" === o2.type && "constructor" === o2.kind ? (r2 && this.raiseRecoverable(o2.start, "Duplicate constructor in the same class"), r2 = true) : o2.key && "PrivateIdentifier" === o2.key.type && Kc(i2, o2) && this.raiseRecoverable(o2.key.start, "Identifier '#" + o2.key.name + "' has already been declared"));
  }
  return this.strict = s2, this.next(), e4.body = this.finishNode(n2, "ClassBody"), this.exitClassBody(), this.finishNode(e4, t2 ? "ClassDeclaration" : "ClassExpression");
}, jc.parseClassElement = function(e4) {
  if (this.eat(fc.semi))
    return null;
  var t2 = this.options.ecmaVersion, s2 = this.startNode(), i2 = "", n2 = false, r2 = false, o2 = "method", a2 = false;
  if (this.eatContextual("static")) {
    if (t2 >= 13 && this.eat(fc.braceL))
      return this.parseClassStaticBlock(s2), s2;
    this.isClassElementNameStart() || this.type === fc.star ? a2 = true : i2 = "static";
  }
  if (s2.static = a2, !i2 && t2 >= 8 && this.eatContextual("async") && (!this.isClassElementNameStart() && this.type !== fc.star || this.canInsertSemicolon() ? i2 = "async" : r2 = true), !i2 && (t2 >= 9 || !r2) && this.eat(fc.star) && (n2 = true), !i2 && !r2 && !n2) {
    var l2 = this.value;
    (this.eatContextual("get") || this.eatContextual("set")) && (this.isClassElementNameStart() ? o2 = l2 : i2 = l2);
  }
  if (i2 ? (s2.computed = false, s2.key = this.startNodeAt(this.lastTokStart, this.lastTokStartLoc), s2.key.name = i2, this.finishNode(s2.key, "Identifier")) : this.parseClassElementName(s2), t2 < 13 || this.type === fc.parenL || "method" !== o2 || n2 || r2) {
    var c2 = !s2.static && Yc(s2, "constructor"), h2 = c2 && e4;
    c2 && "method" !== o2 && this.raise(s2.key.start, "Constructor can't have get/set modifier"), s2.kind = c2 ? "constructor" : o2, this.parseClassMethod(s2, n2, r2, h2);
  } else
    this.parseClassField(s2);
  return s2;
}, jc.isClassElementNameStart = function() {
  return this.type === fc.name || this.type === fc.privateId || this.type === fc.num || this.type === fc.string || this.type === fc.bracketL || this.type.keyword;
}, jc.parseClassElementName = function(e4) {
  this.type === fc.privateId ? ("constructor" === this.value && this.raise(this.start, "Classes can't have an element named '#constructor'"), e4.computed = false, e4.key = this.parsePrivateIdent()) : this.parsePropertyName(e4);
}, jc.parseClassMethod = function(e4, t2, s2, i2) {
  var n2 = e4.key;
  "constructor" === e4.kind ? (t2 && this.raise(n2.start, "Constructor can't be a generator"), s2 && this.raise(n2.start, "Constructor can't be an async method")) : e4.static && Yc(e4, "prototype") && this.raise(n2.start, "Classes may not have a static property named prototype");
  var r2 = e4.value = this.parseMethod(t2, s2, i2);
  return "get" === e4.kind && 0 !== r2.params.length && this.raiseRecoverable(r2.start, "getter should have no params"), "set" === e4.kind && 1 !== r2.params.length && this.raiseRecoverable(r2.start, "setter should have exactly one param"), "set" === e4.kind && "RestElement" === r2.params[0].type && this.raiseRecoverable(r2.params[0].start, "Setter cannot use rest params"), this.finishNode(e4, "MethodDefinition");
}, jc.parseClassField = function(e4) {
  if (Yc(e4, "constructor") ? this.raise(e4.key.start, "Classes can't have a field named 'constructor'") : e4.static && Yc(e4, "prototype") && this.raise(e4.key.start, "Classes can't have a static field named 'prototype'"), this.eat(fc.eq)) {
    var t2 = this.currentThisScope(), s2 = t2.inClassFieldInit;
    t2.inClassFieldInit = true, e4.value = this.parseMaybeAssign(), t2.inClassFieldInit = s2;
  } else
    e4.value = null;
  return this.semicolon(), this.finishNode(e4, "PropertyDefinition");
}, jc.parseClassStaticBlock = function(e4) {
  e4.body = [];
  var t2 = this.labels;
  for (this.labels = [], this.enterScope(320); this.type !== fc.braceR; ) {
    var s2 = this.parseStatement(null);
    e4.body.push(s2);
  }
  return this.next(), this.exitScope(), this.labels = t2, this.finishNode(e4, "StaticBlock");
}, jc.parseClassId = function(e4, t2) {
  this.type === fc.name ? (e4.id = this.parseIdent(), t2 && this.checkLValSimple(e4.id, 2, false)) : (true === t2 && this.unexpected(), e4.id = null);
}, jc.parseClassSuper = function(e4) {
  e4.superClass = this.eat(fc._extends) ? this.parseExprSubscripts(null, false) : null;
}, jc.enterClassBody = function() {
  var e4 = { declared: /* @__PURE__ */ Object.create(null), used: [] };
  return this.privateNameStack.push(e4), e4.declared;
}, jc.exitClassBody = function() {
  var e4 = this.privateNameStack.pop(), t2 = e4.declared, s2 = e4.used;
  if (this.options.checkPrivateFields)
    for (var i2 = this.privateNameStack.length, n2 = 0 === i2 ? null : this.privateNameStack[i2 - 1], r2 = 0; r2 < s2.length; ++r2) {
      var o2 = s2[r2];
      kc(t2, o2.name) || (n2 ? n2.used.push(o2) : this.raiseRecoverable(o2.start, "Private field '#" + o2.name + "' must be declared in an enclosing class"));
    }
}, jc.parseExportAllDeclaration = function(e4, t2) {
  return this.options.ecmaVersion >= 11 && (this.eatContextual("as") ? (e4.exported = this.parseModuleExportName(), this.checkExport(t2, e4.exported, this.lastTokStart)) : e4.exported = null), this.expectContextual("from"), this.type !== fc.string && this.unexpected(), e4.source = this.parseExprAtom(), this.semicolon(), this.finishNode(e4, "ExportAllDeclaration");
}, jc.parseExport = function(e4, t2) {
  if (this.next(), this.eat(fc.star))
    return this.parseExportAllDeclaration(e4, t2);
  if (this.eat(fc._default))
    return this.checkExport(t2, "default", this.lastTokStart), e4.declaration = this.parseExportDefaultDeclaration(), this.finishNode(e4, "ExportDefaultDeclaration");
  if (this.shouldParseExportStatement())
    e4.declaration = this.parseExportDeclaration(e4), "VariableDeclaration" === e4.declaration.type ? this.checkVariableExport(t2, e4.declaration.declarations) : this.checkExport(t2, e4.declaration.id, e4.declaration.id.start), e4.specifiers = [], e4.source = null;
  else {
    if (e4.declaration = null, e4.specifiers = this.parseExportSpecifiers(t2), this.eatContextual("from"))
      this.type !== fc.string && this.unexpected(), e4.source = this.parseExprAtom();
    else {
      for (var s2 = 0, i2 = e4.specifiers; s2 < i2.length; s2 += 1) {
        var n2 = i2[s2];
        this.checkUnreserved(n2.local), this.checkLocalExport(n2.local), "Literal" === n2.local.type && this.raise(n2.local.start, "A string literal cannot be used as an exported binding without `from`.");
      }
      e4.source = null;
    }
    this.semicolon();
  }
  return this.finishNode(e4, "ExportNamedDeclaration");
}, jc.parseExportDeclaration = function(e4) {
  return this.parseStatement(null);
}, jc.parseExportDefaultDeclaration = function() {
  var e4;
  if (this.type === fc._function || (e4 = this.isAsyncFunction())) {
    var t2 = this.startNode();
    return this.next(), e4 && this.next(), this.parseFunction(t2, 4 | qc, false, e4);
  }
  if (this.type === fc._class) {
    var s2 = this.startNode();
    return this.parseClass(s2, "nullableID");
  }
  var i2 = this.parseMaybeAssign();
  return this.semicolon(), i2;
}, jc.checkExport = function(e4, t2, s2) {
  e4 && ("string" != typeof t2 && (t2 = "Identifier" === t2.type ? t2.name : t2.value), kc(e4, t2) && this.raiseRecoverable(s2, "Duplicate export '" + t2 + "'"), e4[t2] = true);
}, jc.checkPatternExport = function(e4, t2) {
  var s2 = t2.type;
  if ("Identifier" === s2)
    this.checkExport(e4, t2, t2.start);
  else if ("ObjectPattern" === s2)
    for (var i2 = 0, n2 = t2.properties; i2 < n2.length; i2 += 1) {
      var r2 = n2[i2];
      this.checkPatternExport(e4, r2);
    }
  else if ("ArrayPattern" === s2)
    for (var o2 = 0, a2 = t2.elements; o2 < a2.length; o2 += 1) {
      var l2 = a2[o2];
      l2 && this.checkPatternExport(e4, l2);
    }
  else
    "Property" === s2 ? this.checkPatternExport(e4, t2.value) : "AssignmentPattern" === s2 ? this.checkPatternExport(e4, t2.left) : "RestElement" === s2 ? this.checkPatternExport(e4, t2.argument) : "ParenthesizedExpression" === s2 && this.checkPatternExport(e4, t2.expression);
}, jc.checkVariableExport = function(e4, t2) {
  if (e4)
    for (var s2 = 0, i2 = t2; s2 < i2.length; s2 += 1) {
      var n2 = i2[s2];
      this.checkPatternExport(e4, n2.id);
    }
}, jc.shouldParseExportStatement = function() {
  return "var" === this.type.keyword || "const" === this.type.keyword || "class" === this.type.keyword || "function" === this.type.keyword || this.isLet() || this.isAsyncFunction();
}, jc.parseExportSpecifier = function(e4) {
  var t2 = this.startNode();
  return t2.local = this.parseModuleExportName(), t2.exported = this.eatContextual("as") ? this.parseModuleExportName() : t2.local, this.checkExport(e4, t2.exported, t2.exported.start), this.finishNode(t2, "ExportSpecifier");
}, jc.parseExportSpecifiers = function(e4) {
  var t2 = [], s2 = true;
  for (this.expect(fc.braceL); !this.eat(fc.braceR); ) {
    if (s2)
      s2 = false;
    else if (this.expect(fc.comma), this.afterTrailingComma(fc.braceR))
      break;
    t2.push(this.parseExportSpecifier(e4));
  }
  return t2;
}, jc.parseImport = function(e4) {
  return this.next(), this.type === fc.string ? (e4.specifiers = Wc, e4.source = this.parseExprAtom()) : (e4.specifiers = this.parseImportSpecifiers(), this.expectContextual("from"), e4.source = this.type === fc.string ? this.parseExprAtom() : this.unexpected()), this.semicolon(), this.finishNode(e4, "ImportDeclaration");
}, jc.parseImportSpecifier = function() {
  var e4 = this.startNode();
  return e4.imported = this.parseModuleExportName(), this.eatContextual("as") ? e4.local = this.parseIdent() : (this.checkUnreserved(e4.imported), e4.local = e4.imported), this.checkLValSimple(e4.local, 2), this.finishNode(e4, "ImportSpecifier");
}, jc.parseImportDefaultSpecifier = function() {
  var e4 = this.startNode();
  return e4.local = this.parseIdent(), this.checkLValSimple(e4.local, 2), this.finishNode(e4, "ImportDefaultSpecifier");
}, jc.parseImportNamespaceSpecifier = function() {
  var e4 = this.startNode();
  return this.next(), this.expectContextual("as"), e4.local = this.parseIdent(), this.checkLValSimple(e4.local, 2), this.finishNode(e4, "ImportNamespaceSpecifier");
}, jc.parseImportSpecifiers = function() {
  var e4 = [], t2 = true;
  if (this.type === fc.name && (e4.push(this.parseImportDefaultSpecifier()), !this.eat(fc.comma)))
    return e4;
  if (this.type === fc.star)
    return e4.push(this.parseImportNamespaceSpecifier()), e4;
  for (this.expect(fc.braceL); !this.eat(fc.braceR); ) {
    if (t2)
      t2 = false;
    else if (this.expect(fc.comma), this.afterTrailingComma(fc.braceR))
      break;
    e4.push(this.parseImportSpecifier());
  }
  return e4;
}, jc.parseModuleExportName = function() {
  if (this.options.ecmaVersion >= 13 && this.type === fc.string) {
    var e4 = this.parseLiteral(this.value);
    return Cc.test(e4.value) && this.raise(e4.start, "An export name cannot include a lone surrogate."), e4;
  }
  return this.parseIdent(true);
}, jc.adaptDirectivePrologue = function(e4) {
  for (var t2 = 0; t2 < e4.length && this.isDirectiveCandidate(e4[t2]); ++t2)
    e4[t2].directive = e4[t2].expression.raw.slice(1, -1);
}, jc.isDirectiveCandidate = function(e4) {
  return this.options.ecmaVersion >= 5 && "ExpressionStatement" === e4.type && "Literal" === e4.expression.type && "string" == typeof e4.expression.value && ('"' === this.input[e4.start] || "'" === this.input[e4.start]);
};
var Xc = Tc.prototype;
Xc.toAssignable = function(e4, t2, s2) {
  if (this.options.ecmaVersion >= 6 && e4)
    switch (e4.type) {
      case "Identifier":
        this.inAsync && "await" === e4.name && this.raise(e4.start, "Cannot use 'await' as identifier inside an async function");
        break;
      case "ObjectPattern":
      case "ArrayPattern":
      case "AssignmentPattern":
      case "RestElement":
        break;
      case "ObjectExpression":
        e4.type = "ObjectPattern", s2 && this.checkPatternErrors(s2, true);
        for (var i2 = 0, n2 = e4.properties; i2 < n2.length; i2 += 1) {
          var r2 = n2[i2];
          this.toAssignable(r2, t2), "RestElement" !== r2.type || "ArrayPattern" !== r2.argument.type && "ObjectPattern" !== r2.argument.type || this.raise(r2.argument.start, "Unexpected token");
        }
        break;
      case "Property":
        "init" !== e4.kind && this.raise(e4.key.start, "Object pattern can't contain getter or setter"), this.toAssignable(e4.value, t2);
        break;
      case "ArrayExpression":
        e4.type = "ArrayPattern", s2 && this.checkPatternErrors(s2, true), this.toAssignableList(e4.elements, t2);
        break;
      case "SpreadElement":
        e4.type = "RestElement", this.toAssignable(e4.argument, t2), "AssignmentPattern" === e4.argument.type && this.raise(e4.argument.start, "Rest elements cannot have a default value");
        break;
      case "AssignmentExpression":
        "=" !== e4.operator && this.raise(e4.left.end, "Only '=' operator can be used for specifying default value."), e4.type = "AssignmentPattern", delete e4.operator, this.toAssignable(e4.left, t2);
        break;
      case "ParenthesizedExpression":
        this.toAssignable(e4.expression, t2, s2);
        break;
      case "ChainExpression":
        this.raiseRecoverable(e4.start, "Optional chaining cannot appear in left-hand side");
        break;
      case "MemberExpression":
        if (!t2)
          break;
      default:
        this.raise(e4.start, "Assigning to rvalue");
    }
  else
    s2 && this.checkPatternErrors(s2, true);
  return e4;
}, Xc.toAssignableList = function(e4, t2) {
  for (var s2 = e4.length, i2 = 0; i2 < s2; i2++) {
    var n2 = e4[i2];
    n2 && this.toAssignable(n2, t2);
  }
  if (s2) {
    var r2 = e4[s2 - 1];
    6 === this.options.ecmaVersion && t2 && r2 && "RestElement" === r2.type && "Identifier" !== r2.argument.type && this.unexpected(r2.argument.start);
  }
  return e4;
}, Xc.parseSpread = function(e4) {
  var t2 = this.startNode();
  return this.next(), t2.argument = this.parseMaybeAssign(false, e4), this.finishNode(t2, "SpreadElement");
}, Xc.parseRestBinding = function() {
  var e4 = this.startNode();
  return this.next(), 6 === this.options.ecmaVersion && this.type !== fc.name && this.unexpected(), e4.argument = this.parseBindingAtom(), this.finishNode(e4, "RestElement");
}, Xc.parseBindingAtom = function() {
  if (this.options.ecmaVersion >= 6)
    switch (this.type) {
      case fc.bracketL:
        var e4 = this.startNode();
        return this.next(), e4.elements = this.parseBindingList(fc.bracketR, true, true), this.finishNode(e4, "ArrayPattern");
      case fc.braceL:
        return this.parseObj(true);
    }
  return this.parseIdent();
}, Xc.parseBindingList = function(e4, t2, s2, i2) {
  for (var n2 = [], r2 = true; !this.eat(e4); )
    if (r2 ? r2 = false : this.expect(fc.comma), t2 && this.type === fc.comma)
      n2.push(null);
    else {
      if (s2 && this.afterTrailingComma(e4))
        break;
      if (this.type === fc.ellipsis) {
        var o2 = this.parseRestBinding();
        this.parseBindingListItem(o2), n2.push(o2), this.type === fc.comma && this.raiseRecoverable(this.start, "Comma is not permitted after the rest element"), this.expect(e4);
        break;
      }
      n2.push(this.parseAssignableListItem(i2));
    }
  return n2;
}, Xc.parseAssignableListItem = function(e4) {
  var t2 = this.parseMaybeDefault(this.start, this.startLoc);
  return this.parseBindingListItem(t2), t2;
}, Xc.parseBindingListItem = function(e4) {
  return e4;
}, Xc.parseMaybeDefault = function(e4, t2, s2) {
  if (s2 = s2 || this.parseBindingAtom(), this.options.ecmaVersion < 6 || !this.eat(fc.eq))
    return s2;
  var i2 = this.startNodeAt(e4, t2);
  return i2.left = s2, i2.right = this.parseMaybeAssign(), this.finishNode(i2, "AssignmentPattern");
}, Xc.checkLValSimple = function(e4, t2, s2) {
  void 0 === t2 && (t2 = 0);
  var i2 = 0 !== t2;
  switch (e4.type) {
    case "Identifier":
      this.strict && this.reservedWordsStrictBind.test(e4.name) && this.raiseRecoverable(e4.start, (i2 ? "Binding " : "Assigning to ") + e4.name + " in strict mode"), i2 && (2 === t2 && "let" === e4.name && this.raiseRecoverable(e4.start, "let is disallowed as a lexically bound name"), s2 && (kc(s2, e4.name) && this.raiseRecoverable(e4.start, "Argument name clash"), s2[e4.name] = true), 5 !== t2 && this.declareName(e4.name, t2, e4.start));
      break;
    case "ChainExpression":
      this.raiseRecoverable(e4.start, "Optional chaining cannot appear in left-hand side");
      break;
    case "MemberExpression":
      i2 && this.raiseRecoverable(e4.start, "Binding member expression");
      break;
    case "ParenthesizedExpression":
      return i2 && this.raiseRecoverable(e4.start, "Binding parenthesized expression"), this.checkLValSimple(e4.expression, t2, s2);
    default:
      this.raise(e4.start, (i2 ? "Binding" : "Assigning to") + " rvalue");
  }
}, Xc.checkLValPattern = function(e4, t2, s2) {
  switch (void 0 === t2 && (t2 = 0), e4.type) {
    case "ObjectPattern":
      for (var i2 = 0, n2 = e4.properties; i2 < n2.length; i2 += 1) {
        var r2 = n2[i2];
        this.checkLValInnerPattern(r2, t2, s2);
      }
      break;
    case "ArrayPattern":
      for (var o2 = 0, a2 = e4.elements; o2 < a2.length; o2 += 1) {
        var l2 = a2[o2];
        l2 && this.checkLValInnerPattern(l2, t2, s2);
      }
      break;
    default:
      this.checkLValSimple(e4, t2, s2);
  }
}, Xc.checkLValInnerPattern = function(e4, t2, s2) {
  switch (void 0 === t2 && (t2 = 0), e4.type) {
    case "Property":
      this.checkLValInnerPattern(e4.value, t2, s2);
      break;
    case "AssignmentPattern":
      this.checkLValPattern(e4.left, t2, s2);
      break;
    case "RestElement":
      this.checkLValPattern(e4.argument, t2, s2);
      break;
    default:
      this.checkLValPattern(e4, t2, s2);
  }
};
var Qc = function(e4, t2, s2, i2, n2) {
  this.token = e4, this.isExpr = !!t2, this.preserveSpace = !!s2, this.override = i2, this.generator = !!n2;
}, Zc = { b_stat: new Qc("{", false), b_expr: new Qc("{", true), b_tmpl: new Qc("${", false), p_stat: new Qc("(", false), p_expr: new Qc("(", true), q_tmpl: new Qc("`", true, true, function(e4) {
  return e4.tryReadTemplateToken();
}), f_stat: new Qc("function", false), f_expr: new Qc("function", true), f_expr_gen: new Qc("function", true, false, null, true), f_gen: new Qc("function", false, false, null, true) }, Jc = Tc.prototype;
Jc.initialContext = function() {
  return [Zc.b_stat];
}, Jc.curContext = function() {
  return this.context[this.context.length - 1];
}, Jc.braceIsBlock = function(e4) {
  var t2 = this.curContext();
  return t2 === Zc.f_expr || t2 === Zc.f_stat || (e4 !== fc.colon || t2 !== Zc.b_stat && t2 !== Zc.b_expr ? e4 === fc._return || e4 === fc.name && this.exprAllowed ? mc.test(this.input.slice(this.lastTokEnd, this.start)) : e4 === fc._else || e4 === fc.semi || e4 === fc.eof || e4 === fc.parenR || e4 === fc.arrow || (e4 === fc.braceL ? t2 === Zc.b_stat : e4 !== fc._var && e4 !== fc._const && e4 !== fc.name && !this.exprAllowed) : !t2.isExpr);
}, Jc.inGeneratorContext = function() {
  for (var e4 = this.context.length - 1; e4 >= 1; e4--) {
    var t2 = this.context[e4];
    if ("function" === t2.token)
      return t2.generator;
  }
  return false;
}, Jc.updateContext = function(e4) {
  var t2, s2 = this.type;
  s2.keyword && e4 === fc.dot ? this.exprAllowed = false : (t2 = s2.updateContext) ? t2.call(this, e4) : this.exprAllowed = s2.beforeExpr;
}, Jc.overrideContext = function(e4) {
  this.curContext() !== e4 && (this.context[this.context.length - 1] = e4);
}, fc.parenR.updateContext = fc.braceR.updateContext = function() {
  if (1 !== this.context.length) {
    var e4 = this.context.pop();
    e4 === Zc.b_stat && "function" === this.curContext().token && (e4 = this.context.pop()), this.exprAllowed = !e4.isExpr;
  } else
    this.exprAllowed = true;
}, fc.braceL.updateContext = function(e4) {
  this.context.push(this.braceIsBlock(e4) ? Zc.b_stat : Zc.b_expr), this.exprAllowed = true;
}, fc.dollarBraceL.updateContext = function() {
  this.context.push(Zc.b_tmpl), this.exprAllowed = true;
}, fc.parenL.updateContext = function(e4) {
  var t2 = e4 === fc._if || e4 === fc._for || e4 === fc._with || e4 === fc._while;
  this.context.push(t2 ? Zc.p_stat : Zc.p_expr), this.exprAllowed = true;
}, fc.incDec.updateContext = function() {
}, fc._function.updateContext = fc._class.updateContext = function(e4) {
  !e4.beforeExpr || e4 === fc._else || e4 === fc.semi && this.curContext() !== Zc.p_stat || e4 === fc._return && mc.test(this.input.slice(this.lastTokEnd, this.start)) || (e4 === fc.colon || e4 === fc.braceL) && this.curContext() === Zc.b_stat ? this.context.push(Zc.f_stat) : this.context.push(Zc.f_expr), this.exprAllowed = false;
}, fc.backQuote.updateContext = function() {
  this.curContext() === Zc.q_tmpl ? this.context.pop() : this.context.push(Zc.q_tmpl), this.exprAllowed = false;
}, fc.star.updateContext = function(e4) {
  if (e4 === fc._function) {
    var t2 = this.context.length - 1;
    this.context[t2] === Zc.f_expr ? this.context[t2] = Zc.f_expr_gen : this.context[t2] = Zc.f_gen;
  }
  this.exprAllowed = true;
}, fc.name.updateContext = function(e4) {
  var t2 = false;
  this.options.ecmaVersion >= 6 && e4 !== fc.dot && ("of" === this.value && !this.exprAllowed || "yield" === this.value && this.inGeneratorContext()) && (t2 = true), this.exprAllowed = t2;
};
var eh = Tc.prototype;
function th(e4) {
  return "MemberExpression" === e4.type && "PrivateIdentifier" === e4.property.type || "ChainExpression" === e4.type && th(e4.expression);
}
eh.checkPropClash = function(e4, t2, s2) {
  if (!(this.options.ecmaVersion >= 9 && "SpreadElement" === e4.type || this.options.ecmaVersion >= 6 && (e4.computed || e4.method || e4.shorthand))) {
    var i2, n2 = e4.key;
    switch (n2.type) {
      case "Identifier":
        i2 = n2.name;
        break;
      case "Literal":
        i2 = String(n2.value);
        break;
      default:
        return;
    }
    var r2 = e4.kind;
    if (this.options.ecmaVersion >= 6)
      "__proto__" === i2 && "init" === r2 && (t2.proto && (s2 ? s2.doubleProto < 0 && (s2.doubleProto = n2.start) : this.raiseRecoverable(n2.start, "Redefinition of __proto__ property")), t2.proto = true);
    else {
      var o2 = t2[i2 = "$" + i2];
      if (o2)
        ("init" === r2 ? this.strict && o2.init || o2.get || o2.set : o2.init || o2[r2]) && this.raiseRecoverable(n2.start, "Redefinition of property");
      else
        o2 = t2[i2] = { init: false, get: false, set: false };
      o2[r2] = true;
    }
  }
}, eh.parseExpression = function(e4, t2) {
  var s2 = this.start, i2 = this.startLoc, n2 = this.parseMaybeAssign(e4, t2);
  if (this.type === fc.comma) {
    var r2 = this.startNodeAt(s2, i2);
    for (r2.expressions = [n2]; this.eat(fc.comma); )
      r2.expressions.push(this.parseMaybeAssign(e4, t2));
    return this.finishNode(r2, "SequenceExpression");
  }
  return n2;
}, eh.parseMaybeAssign = function(e4, t2, s2) {
  if (this.isContextual("yield")) {
    if (this.inGenerator)
      return this.parseYield(e4);
    this.exprAllowed = false;
  }
  var i2 = false, n2 = -1, r2 = -1, o2 = -1;
  t2 ? (n2 = t2.parenthesizedAssign, r2 = t2.trailingComma, o2 = t2.doubleProto, t2.parenthesizedAssign = t2.trailingComma = -1) : (t2 = new Fc(), i2 = true);
  var a2 = this.start, l2 = this.startLoc;
  this.type !== fc.parenL && this.type !== fc.name || (this.potentialArrowAt = this.start, this.potentialArrowInForAwait = "await" === e4);
  var c2 = this.parseMaybeConditional(e4, t2);
  if (s2 && (c2 = s2.call(this, c2, a2, l2)), this.type.isAssign) {
    var h2 = this.startNodeAt(a2, l2);
    return h2.operator = this.value, this.type === fc.eq && (c2 = this.toAssignable(c2, false, t2)), i2 || (t2.parenthesizedAssign = t2.trailingComma = t2.doubleProto = -1), t2.shorthandAssign >= c2.start && (t2.shorthandAssign = -1), this.type === fc.eq ? this.checkLValPattern(c2) : this.checkLValSimple(c2), h2.left = c2, this.next(), h2.right = this.parseMaybeAssign(e4), o2 > -1 && (t2.doubleProto = o2), this.finishNode(h2, "AssignmentExpression");
  }
  return i2 && this.checkExpressionErrors(t2, true), n2 > -1 && (t2.parenthesizedAssign = n2), r2 > -1 && (t2.trailingComma = r2), c2;
}, eh.parseMaybeConditional = function(e4, t2) {
  var s2 = this.start, i2 = this.startLoc, n2 = this.parseExprOps(e4, t2);
  if (this.checkExpressionErrors(t2))
    return n2;
  if (this.eat(fc.question)) {
    var r2 = this.startNodeAt(s2, i2);
    return r2.test = n2, r2.consequent = this.parseMaybeAssign(), this.expect(fc.colon), r2.alternate = this.parseMaybeAssign(e4), this.finishNode(r2, "ConditionalExpression");
  }
  return n2;
}, eh.parseExprOps = function(e4, t2) {
  var s2 = this.start, i2 = this.startLoc, n2 = this.parseMaybeUnary(t2, false, false, e4);
  return this.checkExpressionErrors(t2) || n2.start === s2 && "ArrowFunctionExpression" === n2.type ? n2 : this.parseExprOp(n2, s2, i2, -1, e4);
}, eh.parseExprOp = function(e4, t2, s2, i2, n2) {
  var r2 = this.type.binop;
  if (null != r2 && (!n2 || this.type !== fc._in) && r2 > i2) {
    var o2 = this.type === fc.logicalOR || this.type === fc.logicalAND, a2 = this.type === fc.coalesce;
    a2 && (r2 = fc.logicalAND.binop);
    var l2 = this.value;
    this.next();
    var c2 = this.start, h2 = this.startLoc, u2 = this.parseExprOp(this.parseMaybeUnary(null, false, false, n2), c2, h2, r2, n2), d2 = this.buildBinary(t2, s2, e4, u2, l2, o2 || a2);
    return (o2 && this.type === fc.coalesce || a2 && (this.type === fc.logicalOR || this.type === fc.logicalAND)) && this.raiseRecoverable(this.start, "Logical expressions and coalesce expressions cannot be mixed. Wrap either by parentheses"), this.parseExprOp(d2, t2, s2, i2, n2);
  }
  return e4;
}, eh.buildBinary = function(e4, t2, s2, i2, n2, r2) {
  "PrivateIdentifier" === i2.type && this.raise(i2.start, "Private identifier can only be left side of binary expression");
  var o2 = this.startNodeAt(e4, t2);
  return o2.left = s2, o2.operator = n2, o2.right = i2, this.finishNode(o2, r2 ? "LogicalExpression" : "BinaryExpression");
}, eh.parseMaybeUnary = function(e4, t2, s2, i2) {
  var n2, r2 = this.start, o2 = this.startLoc;
  if (this.isContextual("await") && this.canAwait)
    n2 = this.parseAwait(i2), t2 = true;
  else if (this.type.prefix) {
    var a2 = this.startNode(), l2 = this.type === fc.incDec;
    a2.operator = this.value, a2.prefix = true, this.next(), a2.argument = this.parseMaybeUnary(null, true, l2, i2), this.checkExpressionErrors(e4, true), l2 ? this.checkLValSimple(a2.argument) : this.strict && "delete" === a2.operator && "Identifier" === a2.argument.type ? this.raiseRecoverable(a2.start, "Deleting local variable in strict mode") : "delete" === a2.operator && th(a2.argument) ? this.raiseRecoverable(a2.start, "Private fields can not be deleted") : t2 = true, n2 = this.finishNode(a2, l2 ? "UpdateExpression" : "UnaryExpression");
  } else if (t2 || this.type !== fc.privateId) {
    if (n2 = this.parseExprSubscripts(e4, i2), this.checkExpressionErrors(e4))
      return n2;
    for (; this.type.postfix && !this.canInsertSemicolon(); ) {
      var c2 = this.startNodeAt(r2, o2);
      c2.operator = this.value, c2.prefix = false, c2.argument = n2, this.checkLValSimple(n2), this.next(), n2 = this.finishNode(c2, "UpdateExpression");
    }
  } else
    (i2 || 0 === this.privateNameStack.length) && this.options.checkPrivateFields && this.unexpected(), n2 = this.parsePrivateIdent(), this.type !== fc._in && this.unexpected();
  return s2 || !this.eat(fc.starstar) ? n2 : t2 ? void this.unexpected(this.lastTokStart) : this.buildBinary(r2, o2, n2, this.parseMaybeUnary(null, false, false, i2), "**", false);
}, eh.parseExprSubscripts = function(e4, t2) {
  var s2 = this.start, i2 = this.startLoc, n2 = this.parseExprAtom(e4, t2);
  if ("ArrowFunctionExpression" === n2.type && ")" !== this.input.slice(this.lastTokStart, this.lastTokEnd))
    return n2;
  var r2 = this.parseSubscripts(n2, s2, i2, false, t2);
  return e4 && "MemberExpression" === r2.type && (e4.parenthesizedAssign >= r2.start && (e4.parenthesizedAssign = -1), e4.parenthesizedBind >= r2.start && (e4.parenthesizedBind = -1), e4.trailingComma >= r2.start && (e4.trailingComma = -1)), r2;
}, eh.parseSubscripts = function(e4, t2, s2, i2, n2) {
  for (var r2 = this.options.ecmaVersion >= 8 && "Identifier" === e4.type && "async" === e4.name && this.lastTokEnd === e4.end && !this.canInsertSemicolon() && e4.end - e4.start == 5 && this.potentialArrowAt === e4.start, o2 = false; ; ) {
    var a2 = this.parseSubscript(e4, t2, s2, i2, r2, o2, n2);
    if (a2.optional && (o2 = true), a2 === e4 || "ArrowFunctionExpression" === a2.type) {
      if (o2) {
        var l2 = this.startNodeAt(t2, s2);
        l2.expression = a2, a2 = this.finishNode(l2, "ChainExpression");
      }
      return a2;
    }
    e4 = a2;
  }
}, eh.shouldParseAsyncArrow = function() {
  return !this.canInsertSemicolon() && this.eat(fc.arrow);
}, eh.parseSubscriptAsyncArrow = function(e4, t2, s2, i2) {
  return this.parseArrowExpression(this.startNodeAt(e4, t2), s2, true, i2);
}, eh.parseSubscript = function(e4, t2, s2, i2, n2, r2, o2) {
  var a2 = this.options.ecmaVersion >= 11, l2 = a2 && this.eat(fc.questionDot);
  i2 && l2 && this.raise(this.lastTokStart, "Optional chaining cannot appear in the callee of new expressions");
  var c2 = this.eat(fc.bracketL);
  if (c2 || l2 && this.type !== fc.parenL && this.type !== fc.backQuote || this.eat(fc.dot)) {
    var h2 = this.startNodeAt(t2, s2);
    h2.object = e4, c2 ? (h2.property = this.parseExpression(), this.expect(fc.bracketR)) : this.type === fc.privateId && "Super" !== e4.type ? h2.property = this.parsePrivateIdent() : h2.property = this.parseIdent("never" !== this.options.allowReserved), h2.computed = !!c2, a2 && (h2.optional = l2), e4 = this.finishNode(h2, "MemberExpression");
  } else if (!i2 && this.eat(fc.parenL)) {
    var u2 = new Fc(), d2 = this.yieldPos, p2 = this.awaitPos, f2 = this.awaitIdentPos;
    this.yieldPos = 0, this.awaitPos = 0, this.awaitIdentPos = 0;
    var m2 = this.parseExprList(fc.parenR, this.options.ecmaVersion >= 8, false, u2);
    if (n2 && !l2 && this.shouldParseAsyncArrow())
      return this.checkPatternErrors(u2, false), this.checkYieldAwaitInDefaultParams(), this.awaitIdentPos > 0 && this.raise(this.awaitIdentPos, "Cannot use 'await' as identifier inside an async function"), this.yieldPos = d2, this.awaitPos = p2, this.awaitIdentPos = f2, this.parseSubscriptAsyncArrow(t2, s2, m2, o2);
    this.checkExpressionErrors(u2, true), this.yieldPos = d2 || this.yieldPos, this.awaitPos = p2 || this.awaitPos, this.awaitIdentPos = f2 || this.awaitIdentPos;
    var g2 = this.startNodeAt(t2, s2);
    g2.callee = e4, g2.arguments = m2, a2 && (g2.optional = l2), e4 = this.finishNode(g2, "CallExpression");
  } else if (this.type === fc.backQuote) {
    (l2 || r2) && this.raise(this.start, "Optional chaining cannot appear in the tag of tagged template expressions");
    var y2 = this.startNodeAt(t2, s2);
    y2.tag = e4, y2.quasi = this.parseTemplate({ isTagged: true }), e4 = this.finishNode(y2, "TaggedTemplateExpression");
  }
  return e4;
}, eh.parseExprAtom = function(e4, t2, s2) {
  this.type === fc.slash && this.readRegexp();
  var i2, n2 = this.potentialArrowAt === this.start;
  switch (this.type) {
    case fc._super:
      return this.allowSuper || this.raise(this.start, "'super' keyword outside a method"), i2 = this.startNode(), this.next(), this.type !== fc.parenL || this.allowDirectSuper || this.raise(i2.start, "super() call outside constructor of a subclass"), this.type !== fc.dot && this.type !== fc.bracketL && this.type !== fc.parenL && this.unexpected(), this.finishNode(i2, "Super");
    case fc._this:
      return i2 = this.startNode(), this.next(), this.finishNode(i2, "ThisExpression");
    case fc.name:
      var r2 = this.start, o2 = this.startLoc, a2 = this.containsEsc, l2 = this.parseIdent(false);
      if (this.options.ecmaVersion >= 8 && !a2 && "async" === l2.name && !this.canInsertSemicolon() && this.eat(fc._function))
        return this.overrideContext(Zc.f_expr), this.parseFunction(this.startNodeAt(r2, o2), 0, false, true, t2);
      if (n2 && !this.canInsertSemicolon()) {
        if (this.eat(fc.arrow))
          return this.parseArrowExpression(this.startNodeAt(r2, o2), [l2], false, t2);
        if (this.options.ecmaVersion >= 8 && "async" === l2.name && this.type === fc.name && !a2 && (!this.potentialArrowInForAwait || "of" !== this.value || this.containsEsc))
          return l2 = this.parseIdent(false), !this.canInsertSemicolon() && this.eat(fc.arrow) || this.unexpected(), this.parseArrowExpression(this.startNodeAt(r2, o2), [l2], true, t2);
      }
      return l2;
    case fc.regexp:
      var c2 = this.value;
      return (i2 = this.parseLiteral(c2.value)).regex = { pattern: c2.pattern, flags: c2.flags }, i2;
    case fc.num:
    case fc.string:
      return this.parseLiteral(this.value);
    case fc._null:
    case fc._true:
    case fc._false:
      return (i2 = this.startNode()).value = this.type === fc._null ? null : this.type === fc._true, i2.raw = this.type.keyword, this.next(), this.finishNode(i2, "Literal");
    case fc.parenL:
      var h2 = this.start, u2 = this.parseParenAndDistinguishExpression(n2, t2);
      return e4 && (e4.parenthesizedAssign < 0 && !this.isSimpleAssignTarget(u2) && (e4.parenthesizedAssign = h2), e4.parenthesizedBind < 0 && (e4.parenthesizedBind = h2)), u2;
    case fc.bracketL:
      return i2 = this.startNode(), this.next(), i2.elements = this.parseExprList(fc.bracketR, true, true, e4), this.finishNode(i2, "ArrayExpression");
    case fc.braceL:
      return this.overrideContext(Zc.b_expr), this.parseObj(false, e4);
    case fc._function:
      return i2 = this.startNode(), this.next(), this.parseFunction(i2, 0);
    case fc._class:
      return this.parseClass(this.startNode(), false);
    case fc._new:
      return this.parseNew();
    case fc.backQuote:
      return this.parseTemplate();
    case fc._import:
      return this.options.ecmaVersion >= 11 ? this.parseExprImport(s2) : this.unexpected();
    default:
      return this.parseExprAtomDefault();
  }
}, eh.parseExprAtomDefault = function() {
  this.unexpected();
}, eh.parseExprImport = function(e4) {
  var t2 = this.startNode();
  this.containsEsc && this.raiseRecoverable(this.start, "Escape sequence in keyword import");
  var s2 = this.parseIdent(true);
  return this.type !== fc.parenL || e4 ? this.type === fc.dot ? (t2.meta = s2, this.parseImportMeta(t2)) : void this.unexpected() : this.parseDynamicImport(t2);
}, eh.parseDynamicImport = function(e4) {
  if (this.next(), e4.source = this.parseMaybeAssign(), !this.eat(fc.parenR)) {
    var t2 = this.start;
    this.eat(fc.comma) && this.eat(fc.parenR) ? this.raiseRecoverable(t2, "Trailing comma is not allowed in import()") : this.unexpected(t2);
  }
  return this.finishNode(e4, "ImportExpression");
}, eh.parseImportMeta = function(e4) {
  this.next();
  var t2 = this.containsEsc;
  return e4.property = this.parseIdent(true), "meta" !== e4.property.name && this.raiseRecoverable(e4.property.start, "The only valid meta property for import is 'import.meta'"), t2 && this.raiseRecoverable(e4.start, "'import.meta' must not contain escaped characters"), "module" === this.options.sourceType || this.options.allowImportExportEverywhere || this.raiseRecoverable(e4.start, "Cannot use 'import.meta' outside a module"), this.finishNode(e4, "MetaProperty");
}, eh.parseLiteral = function(e4) {
  var t2 = this.startNode();
  return t2.value = e4, t2.raw = this.input.slice(this.start, this.end), 110 === t2.raw.charCodeAt(t2.raw.length - 1) && (t2.bigint = t2.raw.slice(0, -1).replace(/_/g, "")), this.next(), this.finishNode(t2, "Literal");
}, eh.parseParenExpression = function() {
  this.expect(fc.parenL);
  var e4 = this.parseExpression();
  return this.expect(fc.parenR), e4;
}, eh.shouldParseArrow = function(e4) {
  return !this.canInsertSemicolon();
}, eh.parseParenAndDistinguishExpression = function(e4, t2) {
  var s2, i2 = this.start, n2 = this.startLoc, r2 = this.options.ecmaVersion >= 8;
  if (this.options.ecmaVersion >= 6) {
    this.next();
    var o2, a2 = this.start, l2 = this.startLoc, c2 = [], h2 = true, u2 = false, d2 = new Fc(), p2 = this.yieldPos, f2 = this.awaitPos;
    for (this.yieldPos = 0, this.awaitPos = 0; this.type !== fc.parenR; ) {
      if (h2 ? h2 = false : this.expect(fc.comma), r2 && this.afterTrailingComma(fc.parenR, true)) {
        u2 = true;
        break;
      }
      if (this.type === fc.ellipsis) {
        o2 = this.start, c2.push(this.parseParenItem(this.parseRestBinding())), this.type === fc.comma && this.raiseRecoverable(this.start, "Comma is not permitted after the rest element");
        break;
      }
      c2.push(this.parseMaybeAssign(false, d2, this.parseParenItem));
    }
    var m2 = this.lastTokEnd, g2 = this.lastTokEndLoc;
    if (this.expect(fc.parenR), e4 && this.shouldParseArrow(c2) && this.eat(fc.arrow))
      return this.checkPatternErrors(d2, false), this.checkYieldAwaitInDefaultParams(), this.yieldPos = p2, this.awaitPos = f2, this.parseParenArrowList(i2, n2, c2, t2);
    c2.length && !u2 || this.unexpected(this.lastTokStart), o2 && this.unexpected(o2), this.checkExpressionErrors(d2, true), this.yieldPos = p2 || this.yieldPos, this.awaitPos = f2 || this.awaitPos, c2.length > 1 ? ((s2 = this.startNodeAt(a2, l2)).expressions = c2, this.finishNodeAt(s2, "SequenceExpression", m2, g2)) : s2 = c2[0];
  } else
    s2 = this.parseParenExpression();
  if (this.options.preserveParens) {
    var y2 = this.startNodeAt(i2, n2);
    return y2.expression = s2, this.finishNode(y2, "ParenthesizedExpression");
  }
  return s2;
}, eh.parseParenItem = function(e4) {
  return e4;
}, eh.parseParenArrowList = function(e4, t2, s2, i2) {
  return this.parseArrowExpression(this.startNodeAt(e4, t2), s2, false, i2);
};
var sh = [];
eh.parseNew = function() {
  this.containsEsc && this.raiseRecoverable(this.start, "Escape sequence in keyword new");
  var e4 = this.startNode(), t2 = this.parseIdent(true);
  if (this.options.ecmaVersion >= 6 && this.eat(fc.dot)) {
    e4.meta = t2;
    var s2 = this.containsEsc;
    return e4.property = this.parseIdent(true), "target" !== e4.property.name && this.raiseRecoverable(e4.property.start, "The only valid meta property for new is 'new.target'"), s2 && this.raiseRecoverable(e4.start, "'new.target' must not contain escaped characters"), this.allowNewDotTarget || this.raiseRecoverable(e4.start, "'new.target' can only be used in functions and class static block"), this.finishNode(e4, "MetaProperty");
  }
  var i2 = this.start, n2 = this.startLoc;
  return e4.callee = this.parseSubscripts(this.parseExprAtom(null, false, true), i2, n2, true, false), this.eat(fc.parenL) ? e4.arguments = this.parseExprList(fc.parenR, this.options.ecmaVersion >= 8, false) : e4.arguments = sh, this.finishNode(e4, "NewExpression");
}, eh.parseTemplateElement = function(e4) {
  var t2 = e4.isTagged, s2 = this.startNode();
  return this.type === fc.invalidTemplate ? (t2 || this.raiseRecoverable(this.start, "Bad escape sequence in untagged template literal"), s2.value = { raw: this.value, cooked: null }) : s2.value = { raw: this.input.slice(this.start, this.end).replace(/\r\n?/g, "\n"), cooked: this.value }, this.next(), s2.tail = this.type === fc.backQuote, this.finishNode(s2, "TemplateElement");
}, eh.parseTemplate = function(e4) {
  void 0 === e4 && (e4 = {});
  var t2 = e4.isTagged;
  void 0 === t2 && (t2 = false);
  var s2 = this.startNode();
  this.next(), s2.expressions = [];
  var i2 = this.parseTemplateElement({ isTagged: t2 });
  for (s2.quasis = [i2]; !i2.tail; )
    this.type === fc.eof && this.raise(this.pos, "Unterminated template literal"), this.expect(fc.dollarBraceL), s2.expressions.push(this.parseExpression()), this.expect(fc.braceR), s2.quasis.push(i2 = this.parseTemplateElement({ isTagged: t2 }));
  return this.next(), this.finishNode(s2, "TemplateLiteral");
}, eh.isAsyncProp = function(e4) {
  return !e4.computed && "Identifier" === e4.key.type && "async" === e4.key.name && (this.type === fc.name || this.type === fc.num || this.type === fc.string || this.type === fc.bracketL || this.type.keyword || this.options.ecmaVersion >= 9 && this.type === fc.star) && !mc.test(this.input.slice(this.lastTokEnd, this.start));
}, eh.parseObj = function(e4, t2) {
  var s2 = this.startNode(), i2 = true, n2 = {};
  for (s2.properties = [], this.next(); !this.eat(fc.braceR); ) {
    if (i2)
      i2 = false;
    else if (this.expect(fc.comma), this.options.ecmaVersion >= 5 && this.afterTrailingComma(fc.braceR))
      break;
    var r2 = this.parseProperty(e4, t2);
    e4 || this.checkPropClash(r2, n2, t2), s2.properties.push(r2);
  }
  return this.finishNode(s2, e4 ? "ObjectPattern" : "ObjectExpression");
}, eh.parseProperty = function(e4, t2) {
  var s2, i2, n2, r2, o2 = this.startNode();
  if (this.options.ecmaVersion >= 9 && this.eat(fc.ellipsis))
    return e4 ? (o2.argument = this.parseIdent(false), this.type === fc.comma && this.raiseRecoverable(this.start, "Comma is not permitted after the rest element"), this.finishNode(o2, "RestElement")) : (o2.argument = this.parseMaybeAssign(false, t2), this.type === fc.comma && t2 && t2.trailingComma < 0 && (t2.trailingComma = this.start), this.finishNode(o2, "SpreadElement"));
  this.options.ecmaVersion >= 6 && (o2.method = false, o2.shorthand = false, (e4 || t2) && (n2 = this.start, r2 = this.startLoc), e4 || (s2 = this.eat(fc.star)));
  var a2 = this.containsEsc;
  return this.parsePropertyName(o2), !e4 && !a2 && this.options.ecmaVersion >= 8 && !s2 && this.isAsyncProp(o2) ? (i2 = true, s2 = this.options.ecmaVersion >= 9 && this.eat(fc.star), this.parsePropertyName(o2)) : i2 = false, this.parsePropertyValue(o2, e4, s2, i2, n2, r2, t2, a2), this.finishNode(o2, "Property");
}, eh.parseGetterSetter = function(e4) {
  e4.kind = e4.key.name, this.parsePropertyName(e4), e4.value = this.parseMethod(false);
  var t2 = "get" === e4.kind ? 0 : 1;
  if (e4.value.params.length !== t2) {
    var s2 = e4.value.start;
    "get" === e4.kind ? this.raiseRecoverable(s2, "getter should have no params") : this.raiseRecoverable(s2, "setter should have exactly one param");
  } else
    "set" === e4.kind && "RestElement" === e4.value.params[0].type && this.raiseRecoverable(e4.value.params[0].start, "Setter cannot use rest params");
}, eh.parsePropertyValue = function(e4, t2, s2, i2, n2, r2, o2, a2) {
  (s2 || i2) && this.type === fc.colon && this.unexpected(), this.eat(fc.colon) ? (e4.value = t2 ? this.parseMaybeDefault(this.start, this.startLoc) : this.parseMaybeAssign(false, o2), e4.kind = "init") : this.options.ecmaVersion >= 6 && this.type === fc.parenL ? (t2 && this.unexpected(), e4.kind = "init", e4.method = true, e4.value = this.parseMethod(s2, i2)) : t2 || a2 || !(this.options.ecmaVersion >= 5) || e4.computed || "Identifier" !== e4.key.type || "get" !== e4.key.name && "set" !== e4.key.name || this.type === fc.comma || this.type === fc.braceR || this.type === fc.eq ? this.options.ecmaVersion >= 6 && !e4.computed && "Identifier" === e4.key.type ? ((s2 || i2) && this.unexpected(), this.checkUnreserved(e4.key), "await" !== e4.key.name || this.awaitIdentPos || (this.awaitIdentPos = n2), e4.kind = "init", t2 ? e4.value = this.parseMaybeDefault(n2, r2, this.copyNode(e4.key)) : this.type === fc.eq && o2 ? (o2.shorthandAssign < 0 && (o2.shorthandAssign = this.start), e4.value = this.parseMaybeDefault(n2, r2, this.copyNode(e4.key))) : e4.value = this.copyNode(e4.key), e4.shorthand = true) : this.unexpected() : ((s2 || i2) && this.unexpected(), this.parseGetterSetter(e4));
}, eh.parsePropertyName = function(e4) {
  if (this.options.ecmaVersion >= 6) {
    if (this.eat(fc.bracketL))
      return e4.computed = true, e4.key = this.parseMaybeAssign(), this.expect(fc.bracketR), e4.key;
    e4.computed = false;
  }
  return e4.key = this.type === fc.num || this.type === fc.string ? this.parseExprAtom() : this.parseIdent("never" !== this.options.allowReserved);
}, eh.initFunction = function(e4) {
  e4.id = null, this.options.ecmaVersion >= 6 && (e4.generator = e4.expression = false), this.options.ecmaVersion >= 8 && (e4.async = false);
}, eh.parseMethod = function(e4, t2, s2) {
  var i2 = this.startNode(), n2 = this.yieldPos, r2 = this.awaitPos, o2 = this.awaitIdentPos;
  return this.initFunction(i2), this.options.ecmaVersion >= 6 && (i2.generator = e4), this.options.ecmaVersion >= 8 && (i2.async = !!t2), this.yieldPos = 0, this.awaitPos = 0, this.awaitIdentPos = 0, this.enterScope(64 | Lc(t2, i2.generator) | (s2 ? 128 : 0)), this.expect(fc.parenL), i2.params = this.parseBindingList(fc.parenR, false, this.options.ecmaVersion >= 8), this.checkYieldAwaitInDefaultParams(), this.parseFunctionBody(i2, false, true, false), this.yieldPos = n2, this.awaitPos = r2, this.awaitIdentPos = o2, this.finishNode(i2, "FunctionExpression");
}, eh.parseArrowExpression = function(e4, t2, s2, i2) {
  var n2 = this.yieldPos, r2 = this.awaitPos, o2 = this.awaitIdentPos;
  return this.enterScope(16 | Lc(s2, false)), this.initFunction(e4), this.options.ecmaVersion >= 8 && (e4.async = !!s2), this.yieldPos = 0, this.awaitPos = 0, this.awaitIdentPos = 0, e4.params = this.toAssignableList(t2, true), this.parseFunctionBody(e4, true, false, i2), this.yieldPos = n2, this.awaitPos = r2, this.awaitIdentPos = o2, this.finishNode(e4, "ArrowFunctionExpression");
}, eh.parseFunctionBody = function(e4, t2, s2, i2) {
  var n2 = t2 && this.type !== fc.braceL, r2 = this.strict, o2 = false;
  if (n2)
    e4.body = this.parseMaybeAssign(i2), e4.expression = true, this.checkParams(e4, false);
  else {
    var a2 = this.options.ecmaVersion >= 7 && !this.isSimpleParamList(e4.params);
    r2 && !a2 || (o2 = this.strictDirective(this.end)) && a2 && this.raiseRecoverable(e4.start, "Illegal 'use strict' directive in function with non-simple parameter list");
    var l2 = this.labels;
    this.labels = [], o2 && (this.strict = true), this.checkParams(e4, !r2 && !o2 && !t2 && !s2 && this.isSimpleParamList(e4.params)), this.strict && e4.id && this.checkLValSimple(e4.id, 5), e4.body = this.parseBlock(false, void 0, o2 && !r2), e4.expression = false, this.adaptDirectivePrologue(e4.body.body), this.labels = l2;
  }
  this.exitScope();
}, eh.isSimpleParamList = function(e4) {
  for (var t2 = 0, s2 = e4; t2 < s2.length; t2 += 1) {
    if ("Identifier" !== s2[t2].type)
      return false;
  }
  return true;
}, eh.checkParams = function(e4, t2) {
  for (var s2 = /* @__PURE__ */ Object.create(null), i2 = 0, n2 = e4.params; i2 < n2.length; i2 += 1) {
    var r2 = n2[i2];
    this.checkLValInnerPattern(r2, 1, t2 ? null : s2);
  }
}, eh.parseExprList = function(e4, t2, s2, i2) {
  for (var n2 = [], r2 = true; !this.eat(e4); ) {
    if (r2)
      r2 = false;
    else if (this.expect(fc.comma), t2 && this.afterTrailingComma(e4))
      break;
    var o2 = void 0;
    s2 && this.type === fc.comma ? o2 = null : this.type === fc.ellipsis ? (o2 = this.parseSpread(i2), i2 && this.type === fc.comma && i2.trailingComma < 0 && (i2.trailingComma = this.start)) : o2 = this.parseMaybeAssign(false, i2), n2.push(o2);
  }
  return n2;
}, eh.checkUnreserved = function(e4) {
  var t2 = e4.start, s2 = e4.end, i2 = e4.name;
  (this.inGenerator && "yield" === i2 && this.raiseRecoverable(t2, "Cannot use 'yield' as identifier inside a generator"), this.inAsync && "await" === i2 && this.raiseRecoverable(t2, "Cannot use 'await' as identifier inside an async function"), this.currentThisScope().inClassFieldInit && "arguments" === i2 && this.raiseRecoverable(t2, "Cannot use 'arguments' in class field initializer"), !this.inClassStaticBlock || "arguments" !== i2 && "await" !== i2 || this.raise(t2, "Cannot use " + i2 + " in class static initialization block"), this.keywords.test(i2) && this.raise(t2, "Unexpected keyword '" + i2 + "'"), this.options.ecmaVersion < 6 && -1 !== this.input.slice(t2, s2).indexOf("\\")) || (this.strict ? this.reservedWordsStrict : this.reservedWords).test(i2) && (this.inAsync || "await" !== i2 || this.raiseRecoverable(t2, "Cannot use keyword 'await' outside an async function"), this.raiseRecoverable(t2, "The keyword '" + i2 + "' is reserved"));
}, eh.parseIdent = function(e4) {
  var t2 = this.parseIdentNode();
  return this.next(!!e4), this.finishNode(t2, "Identifier"), e4 || (this.checkUnreserved(t2), "await" !== t2.name || this.awaitIdentPos || (this.awaitIdentPos = t2.start)), t2;
}, eh.parseIdentNode = function() {
  var e4 = this.startNode();
  return this.type === fc.name ? e4.name = this.value : this.type.keyword ? (e4.name = this.type.keyword, "class" !== e4.name && "function" !== e4.name || this.lastTokEnd === this.lastTokStart + 1 && 46 === this.input.charCodeAt(this.lastTokStart) || this.context.pop()) : this.unexpected(), e4;
}, eh.parsePrivateIdent = function() {
  var e4 = this.startNode();
  return this.type === fc.privateId ? e4.name = this.value : this.unexpected(), this.next(), this.finishNode(e4, "PrivateIdentifier"), this.options.checkPrivateFields && (0 === this.privateNameStack.length ? this.raise(e4.start, "Private field '#" + e4.name + "' must be declared in an enclosing class") : this.privateNameStack[this.privateNameStack.length - 1].used.push(e4)), e4;
}, eh.parseYield = function(e4) {
  this.yieldPos || (this.yieldPos = this.start);
  var t2 = this.startNode();
  return this.next(), this.type === fc.semi || this.canInsertSemicolon() || this.type !== fc.star && !this.type.startsExpr ? (t2.delegate = false, t2.argument = null) : (t2.delegate = this.eat(fc.star), t2.argument = this.parseMaybeAssign(e4)), this.finishNode(t2, "YieldExpression");
}, eh.parseAwait = function(e4) {
  this.awaitPos || (this.awaitPos = this.start);
  var t2 = this.startNode();
  return this.next(), t2.argument = this.parseMaybeUnary(null, true, false, e4), this.finishNode(t2, "AwaitExpression");
};
var ih = Tc.prototype;
ih.raise = function(e4, t2) {
  var s2 = _c(this.input, e4);
  t2 += " (" + s2.line + ":" + s2.column + ")";
  var i2 = new SyntaxError(t2);
  throw i2.pos = e4, i2.loc = s2, i2.raisedAt = this.pos, i2;
}, ih.raiseRecoverable = ih.raise, ih.curPosition = function() {
  if (this.options.locations)
    return new $c(this.curLine, this.pos - this.lineStart);
};
var nh = Tc.prototype, rh = function(e4) {
  this.flags = e4, this.var = [], this.lexical = [], this.functions = [], this.inClassFieldInit = false;
};
nh.enterScope = function(e4) {
  this.scopeStack.push(new rh(e4));
}, nh.exitScope = function() {
  this.scopeStack.pop();
}, nh.treatFunctionsAsVarInScope = function(e4) {
  return 2 & e4.flags || !this.inModule && 1 & e4.flags;
}, nh.declareName = function(e4, t2, s2) {
  var i2 = false;
  if (2 === t2) {
    var n2 = this.currentScope();
    i2 = n2.lexical.indexOf(e4) > -1 || n2.functions.indexOf(e4) > -1 || n2.var.indexOf(e4) > -1, n2.lexical.push(e4), this.inModule && 1 & n2.flags && delete this.undefinedExports[e4];
  } else if (4 === t2) {
    this.currentScope().lexical.push(e4);
  } else if (3 === t2) {
    var r2 = this.currentScope();
    i2 = this.treatFunctionsAsVar ? r2.lexical.indexOf(e4) > -1 : r2.lexical.indexOf(e4) > -1 || r2.var.indexOf(e4) > -1, r2.functions.push(e4);
  } else
    for (var o2 = this.scopeStack.length - 1; o2 >= 0; --o2) {
      var a2 = this.scopeStack[o2];
      if (a2.lexical.indexOf(e4) > -1 && !(32 & a2.flags && a2.lexical[0] === e4) || !this.treatFunctionsAsVarInScope(a2) && a2.functions.indexOf(e4) > -1) {
        i2 = true;
        break;
      }
      if (a2.var.push(e4), this.inModule && 1 & a2.flags && delete this.undefinedExports[e4], 259 & a2.flags)
        break;
    }
  i2 && this.raiseRecoverable(s2, "Identifier '" + e4 + "' has already been declared");
}, nh.checkLocalExport = function(e4) {
  -1 === this.scopeStack[0].lexical.indexOf(e4.name) && -1 === this.scopeStack[0].var.indexOf(e4.name) && (this.undefinedExports[e4.name] = e4);
}, nh.currentScope = function() {
  return this.scopeStack[this.scopeStack.length - 1];
}, nh.currentVarScope = function() {
  for (var e4 = this.scopeStack.length - 1; ; e4--) {
    var t2 = this.scopeStack[e4];
    if (259 & t2.flags)
      return t2;
  }
}, nh.currentThisScope = function() {
  for (var e4 = this.scopeStack.length - 1; ; e4--) {
    var t2 = this.scopeStack[e4];
    if (259 & t2.flags && !(16 & t2.flags))
      return t2;
  }
};
var oh = function(e4, t2, s2) {
  this.type = "", this.start = t2, this.end = 0, e4.options.locations && (this.loc = new Nc(e4, s2)), e4.options.directSourceFile && (this.sourceFile = e4.options.directSourceFile), e4.options.ranges && (this.range = [t2, 0]);
}, ah = Tc.prototype;
function lh(e4, t2, s2, i2) {
  return e4.type = t2, e4.end = s2, this.options.locations && (e4.loc.end = i2), this.options.ranges && (e4.range[1] = s2), e4;
}
ah.startNode = function() {
  return new oh(this, this.start, this.startLoc);
}, ah.startNodeAt = function(e4, t2) {
  return new oh(this, e4, t2);
}, ah.finishNode = function(e4, t2) {
  return lh.call(this, e4, t2, this.lastTokEnd, this.lastTokEndLoc);
}, ah.finishNodeAt = function(e4, t2, s2, i2) {
  return lh.call(this, e4, t2, s2, i2);
}, ah.copyNode = function(e4) {
  var t2 = new oh(this, e4.start, this.startLoc);
  for (var s2 in e4)
    t2[s2] = e4[s2];
  return t2;
};
var ch = "ASCII ASCII_Hex_Digit AHex Alphabetic Alpha Any Assigned Bidi_Control Bidi_C Bidi_Mirrored Bidi_M Case_Ignorable CI Cased Changes_When_Casefolded CWCF Changes_When_Casemapped CWCM Changes_When_Lowercased CWL Changes_When_NFKC_Casefolded CWKCF Changes_When_Titlecased CWT Changes_When_Uppercased CWU Dash Default_Ignorable_Code_Point DI Deprecated Dep Diacritic Dia Emoji Emoji_Component Emoji_Modifier Emoji_Modifier_Base Emoji_Presentation Extender Ext Grapheme_Base Gr_Base Grapheme_Extend Gr_Ext Hex_Digit Hex IDS_Binary_Operator IDSB IDS_Trinary_Operator IDST ID_Continue IDC ID_Start IDS Ideographic Ideo Join_Control Join_C Logical_Order_Exception LOE Lowercase Lower Math Noncharacter_Code_Point NChar Pattern_Syntax Pat_Syn Pattern_White_Space Pat_WS Quotation_Mark QMark Radical Regional_Indicator RI Sentence_Terminal STerm Soft_Dotted SD Terminal_Punctuation Term Unified_Ideograph UIdeo Uppercase Upper Variation_Selector VS White_Space space XID_Continue XIDC XID_Start XIDS", hh = ch + " Extended_Pictographic", uh = hh + " EBase EComp EMod EPres ExtPict", dh = { 9: ch, 10: hh, 11: hh, 12: uh, 13: uh, 14: uh }, ph = { 9: "", 10: "", 11: "", 12: "", 13: "", 14: "Basic_Emoji Emoji_Keycap_Sequence RGI_Emoji_Modifier_Sequence RGI_Emoji_Flag_Sequence RGI_Emoji_Tag_Sequence RGI_Emoji_ZWJ_Sequence RGI_Emoji" }, fh = "Cased_Letter LC Close_Punctuation Pe Connector_Punctuation Pc Control Cc cntrl Currency_Symbol Sc Dash_Punctuation Pd Decimal_Number Nd digit Enclosing_Mark Me Final_Punctuation Pf Format Cf Initial_Punctuation Pi Letter L Letter_Number Nl Line_Separator Zl Lowercase_Letter Ll Mark M Combining_Mark Math_Symbol Sm Modifier_Letter Lm Modifier_Symbol Sk Nonspacing_Mark Mn Number N Open_Punctuation Ps Other C Other_Letter Lo Other_Number No Other_Punctuation Po Other_Symbol So Paragraph_Separator Zp Private_Use Co Punctuation P punct Separator Z Space_Separator Zs Spacing_Mark Mc Surrogate Cs Symbol S Titlecase_Letter Lt Unassigned Cn Uppercase_Letter Lu", mh = "Adlam Adlm Ahom Anatolian_Hieroglyphs Hluw Arabic Arab Armenian Armn Avestan Avst Balinese Bali Bamum Bamu Bassa_Vah Bass Batak Batk Bengali Beng Bhaiksuki Bhks Bopomofo Bopo Brahmi Brah Braille Brai Buginese Bugi Buhid Buhd Canadian_Aboriginal Cans Carian Cari Caucasian_Albanian Aghb Chakma Cakm Cham Cham Cherokee Cher Common Zyyy Coptic Copt Qaac Cuneiform Xsux Cypriot Cprt Cyrillic Cyrl Deseret Dsrt Devanagari Deva Duployan Dupl Egyptian_Hieroglyphs Egyp Elbasan Elba Ethiopic Ethi Georgian Geor Glagolitic Glag Gothic Goth Grantha Gran Greek Grek Gujarati Gujr Gurmukhi Guru Han Hani Hangul Hang Hanunoo Hano Hatran Hatr Hebrew Hebr Hiragana Hira Imperial_Aramaic Armi Inherited Zinh Qaai Inscriptional_Pahlavi Phli Inscriptional_Parthian Prti Javanese Java Kaithi Kthi Kannada Knda Katakana Kana Kayah_Li Kali Kharoshthi Khar Khmer Khmr Khojki Khoj Khudawadi Sind Lao Laoo Latin Latn Lepcha Lepc Limbu Limb Linear_A Lina Linear_B Linb Lisu Lisu Lycian Lyci Lydian Lydi Mahajani Mahj Malayalam Mlym Mandaic Mand Manichaean Mani Marchen Marc Masaram_Gondi Gonm Meetei_Mayek Mtei Mende_Kikakui Mend Meroitic_Cursive Merc Meroitic_Hieroglyphs Mero Miao Plrd Modi Mongolian Mong Mro Mroo Multani Mult Myanmar Mymr Nabataean Nbat New_Tai_Lue Talu Newa Newa Nko Nkoo Nushu Nshu Ogham Ogam Ol_Chiki Olck Old_Hungarian Hung Old_Italic Ital Old_North_Arabian Narb Old_Permic Perm Old_Persian Xpeo Old_South_Arabian Sarb Old_Turkic Orkh Oriya Orya Osage Osge Osmanya Osma Pahawh_Hmong Hmng Palmyrene Palm Pau_Cin_Hau Pauc Phags_Pa Phag Phoenician Phnx Psalter_Pahlavi Phlp Rejang Rjng Runic Runr Samaritan Samr Saurashtra Saur Sharada Shrd Shavian Shaw Siddham Sidd SignWriting Sgnw Sinhala Sinh Sora_Sompeng Sora Soyombo Soyo Sundanese Sund Syloti_Nagri Sylo Syriac Syrc Tagalog Tglg Tagbanwa Tagb Tai_Le Tale Tai_Tham Lana Tai_Viet Tavt Takri Takr Tamil Taml Tangut Tang Telugu Telu Thaana Thaa Thai Thai Tibetan Tibt Tifinagh Tfng Tirhuta Tirh Ugaritic Ugar Vai Vaii Warang_Citi Wara Yi Yiii Zanabazar_Square Zanb", gh = mh + " Dogra Dogr Gunjala_Gondi Gong Hanifi_Rohingya Rohg Makasar Maka Medefaidrin Medf Old_Sogdian Sogo Sogdian Sogd", yh = gh + " Elymaic Elym Nandinagari Nand Nyiakeng_Puachue_Hmong Hmnp Wancho Wcho", xh = yh + " Chorasmian Chrs Diak Dives_Akuru Khitan_Small_Script Kits Yezi Yezidi", Eh = xh + " Cypro_Minoan Cpmn Old_Uyghur Ougr Tangsa Tnsa Toto Vithkuqi Vith", bh = { 9: mh, 10: gh, 11: yh, 12: xh, 13: Eh, 14: Eh + " Hrkt Katakana_Or_Hiragana Kawi Nag_Mundari Nagm Unknown Zzzz" }, vh = {};
function Sh(e4) {
  var t2 = vh[e4] = { binary: wc(dh[e4] + " " + fh), binaryOfStrings: wc(ph[e4]), nonBinary: { General_Category: wc(fh), Script: wc(bh[e4]) } };
  t2.nonBinary.Script_Extensions = t2.nonBinary.Script, t2.nonBinary.gc = t2.nonBinary.General_Category, t2.nonBinary.sc = t2.nonBinary.Script, t2.nonBinary.scx = t2.nonBinary.Script_Extensions;
}
for (var Ah = 0, kh = [9, 10, 11, 12, 13, 14]; Ah < kh.length; Ah += 1) {
  Sh(kh[Ah]);
}
var Ih = Tc.prototype, wh = function(e4) {
  this.parser = e4, this.validFlags = "gim" + (e4.options.ecmaVersion >= 6 ? "uy" : "") + (e4.options.ecmaVersion >= 9 ? "s" : "") + (e4.options.ecmaVersion >= 13 ? "d" : "") + (e4.options.ecmaVersion >= 15 ? "v" : ""), this.unicodeProperties = vh[e4.options.ecmaVersion >= 14 ? 14 : e4.options.ecmaVersion], this.source = "", this.flags = "", this.start = 0, this.switchU = false, this.switchV = false, this.switchN = false, this.pos = 0, this.lastIntValue = 0, this.lastStringValue = "", this.lastAssertionIsQuantifiable = false, this.numCapturingParens = 0, this.maxBackReference = 0, this.groupNames = [], this.backReferenceNames = [];
};
function Ph(e4) {
  return 36 === e4 || e4 >= 40 && e4 <= 43 || 46 === e4 || 63 === e4 || e4 >= 91 && e4 <= 94 || e4 >= 123 && e4 <= 125;
}
function Ch(e4) {
  return e4 >= 65 && e4 <= 90 || e4 >= 97 && e4 <= 122;
}
wh.prototype.reset = function(e4, t2, s2) {
  var i2 = -1 !== s2.indexOf("v"), n2 = -1 !== s2.indexOf("u");
  this.start = 0 | e4, this.source = t2 + "", this.flags = s2, i2 && this.parser.options.ecmaVersion >= 15 ? (this.switchU = true, this.switchV = true, this.switchN = true) : (this.switchU = n2 && this.parser.options.ecmaVersion >= 6, this.switchV = false, this.switchN = n2 && this.parser.options.ecmaVersion >= 9);
}, wh.prototype.raise = function(e4) {
  this.parser.raiseRecoverable(this.start, "Invalid regular expression: /" + this.source + "/: " + e4);
}, wh.prototype.at = function(e4, t2) {
  void 0 === t2 && (t2 = false);
  var s2 = this.source, i2 = s2.length;
  if (e4 >= i2)
    return -1;
  var n2 = s2.charCodeAt(e4);
  if (!t2 && !this.switchU || n2 <= 55295 || n2 >= 57344 || e4 + 1 >= i2)
    return n2;
  var r2 = s2.charCodeAt(e4 + 1);
  return r2 >= 56320 && r2 <= 57343 ? (n2 << 10) + r2 - 56613888 : n2;
}, wh.prototype.nextIndex = function(e4, t2) {
  void 0 === t2 && (t2 = false);
  var s2 = this.source, i2 = s2.length;
  if (e4 >= i2)
    return i2;
  var n2, r2 = s2.charCodeAt(e4);
  return !t2 && !this.switchU || r2 <= 55295 || r2 >= 57344 || e4 + 1 >= i2 || (n2 = s2.charCodeAt(e4 + 1)) < 56320 || n2 > 57343 ? e4 + 1 : e4 + 2;
}, wh.prototype.current = function(e4) {
  return void 0 === e4 && (e4 = false), this.at(this.pos, e4);
}, wh.prototype.lookahead = function(e4) {
  return void 0 === e4 && (e4 = false), this.at(this.nextIndex(this.pos, e4), e4);
}, wh.prototype.advance = function(e4) {
  void 0 === e4 && (e4 = false), this.pos = this.nextIndex(this.pos, e4);
}, wh.prototype.eat = function(e4, t2) {
  return void 0 === t2 && (t2 = false), this.current(t2) === e4 && (this.advance(t2), true);
}, wh.prototype.eatChars = function(e4, t2) {
  void 0 === t2 && (t2 = false);
  for (var s2 = this.pos, i2 = 0, n2 = e4; i2 < n2.length; i2 += 1) {
    var r2 = n2[i2], o2 = this.at(s2, t2);
    if (-1 === o2 || o2 !== r2)
      return false;
    s2 = this.nextIndex(s2, t2);
  }
  return this.pos = s2, true;
}, Ih.validateRegExpFlags = function(e4) {
  for (var t2 = e4.validFlags, s2 = e4.flags, i2 = false, n2 = false, r2 = 0; r2 < s2.length; r2++) {
    var o2 = s2.charAt(r2);
    -1 === t2.indexOf(o2) && this.raise(e4.start, "Invalid regular expression flag"), s2.indexOf(o2, r2 + 1) > -1 && this.raise(e4.start, "Duplicate regular expression flag"), "u" === o2 && (i2 = true), "v" === o2 && (n2 = true);
  }
  this.options.ecmaVersion >= 15 && i2 && n2 && this.raise(e4.start, "Invalid regular expression flag");
}, Ih.validateRegExpPattern = function(e4) {
  this.regexp_pattern(e4), !e4.switchN && this.options.ecmaVersion >= 9 && e4.groupNames.length > 0 && (e4.switchN = true, this.regexp_pattern(e4));
}, Ih.regexp_pattern = function(e4) {
  e4.pos = 0, e4.lastIntValue = 0, e4.lastStringValue = "", e4.lastAssertionIsQuantifiable = false, e4.numCapturingParens = 0, e4.maxBackReference = 0, e4.groupNames.length = 0, e4.backReferenceNames.length = 0, this.regexp_disjunction(e4), e4.pos !== e4.source.length && (e4.eat(41) && e4.raise("Unmatched ')'"), (e4.eat(93) || e4.eat(125)) && e4.raise("Lone quantifier brackets")), e4.maxBackReference > e4.numCapturingParens && e4.raise("Invalid escape");
  for (var t2 = 0, s2 = e4.backReferenceNames; t2 < s2.length; t2 += 1) {
    var i2 = s2[t2];
    -1 === e4.groupNames.indexOf(i2) && e4.raise("Invalid named capture referenced");
  }
}, Ih.regexp_disjunction = function(e4) {
  for (this.regexp_alternative(e4); e4.eat(124); )
    this.regexp_alternative(e4);
  this.regexp_eatQuantifier(e4, true) && e4.raise("Nothing to repeat"), e4.eat(123) && e4.raise("Lone quantifier brackets");
}, Ih.regexp_alternative = function(e4) {
  for (; e4.pos < e4.source.length && this.regexp_eatTerm(e4); )
    ;
}, Ih.regexp_eatTerm = function(e4) {
  return this.regexp_eatAssertion(e4) ? (e4.lastAssertionIsQuantifiable && this.regexp_eatQuantifier(e4) && e4.switchU && e4.raise("Invalid quantifier"), true) : !!(e4.switchU ? this.regexp_eatAtom(e4) : this.regexp_eatExtendedAtom(e4)) && (this.regexp_eatQuantifier(e4), true);
}, Ih.regexp_eatAssertion = function(e4) {
  var t2 = e4.pos;
  if (e4.lastAssertionIsQuantifiable = false, e4.eat(94) || e4.eat(36))
    return true;
  if (e4.eat(92)) {
    if (e4.eat(66) || e4.eat(98))
      return true;
    e4.pos = t2;
  }
  if (e4.eat(40) && e4.eat(63)) {
    var s2 = false;
    if (this.options.ecmaVersion >= 9 && (s2 = e4.eat(60)), e4.eat(61) || e4.eat(33))
      return this.regexp_disjunction(e4), e4.eat(41) || e4.raise("Unterminated group"), e4.lastAssertionIsQuantifiable = !s2, true;
  }
  return e4.pos = t2, false;
}, Ih.regexp_eatQuantifier = function(e4, t2) {
  return void 0 === t2 && (t2 = false), !!this.regexp_eatQuantifierPrefix(e4, t2) && (e4.eat(63), true);
}, Ih.regexp_eatQuantifierPrefix = function(e4, t2) {
  return e4.eat(42) || e4.eat(43) || e4.eat(63) || this.regexp_eatBracedQuantifier(e4, t2);
}, Ih.regexp_eatBracedQuantifier = function(e4, t2) {
  var s2 = e4.pos;
  if (e4.eat(123)) {
    var i2 = 0, n2 = -1;
    if (this.regexp_eatDecimalDigits(e4) && (i2 = e4.lastIntValue, e4.eat(44) && this.regexp_eatDecimalDigits(e4) && (n2 = e4.lastIntValue), e4.eat(125)))
      return -1 !== n2 && n2 < i2 && !t2 && e4.raise("numbers out of order in {} quantifier"), true;
    e4.switchU && !t2 && e4.raise("Incomplete quantifier"), e4.pos = s2;
  }
  return false;
}, Ih.regexp_eatAtom = function(e4) {
  return this.regexp_eatPatternCharacters(e4) || e4.eat(46) || this.regexp_eatReverseSolidusAtomEscape(e4) || this.regexp_eatCharacterClass(e4) || this.regexp_eatUncapturingGroup(e4) || this.regexp_eatCapturingGroup(e4);
}, Ih.regexp_eatReverseSolidusAtomEscape = function(e4) {
  var t2 = e4.pos;
  if (e4.eat(92)) {
    if (this.regexp_eatAtomEscape(e4))
      return true;
    e4.pos = t2;
  }
  return false;
}, Ih.regexp_eatUncapturingGroup = function(e4) {
  var t2 = e4.pos;
  if (e4.eat(40)) {
    if (e4.eat(63) && e4.eat(58)) {
      if (this.regexp_disjunction(e4), e4.eat(41))
        return true;
      e4.raise("Unterminated group");
    }
    e4.pos = t2;
  }
  return false;
}, Ih.regexp_eatCapturingGroup = function(e4) {
  if (e4.eat(40)) {
    if (this.options.ecmaVersion >= 9 ? this.regexp_groupSpecifier(e4) : 63 === e4.current() && e4.raise("Invalid group"), this.regexp_disjunction(e4), e4.eat(41))
      return e4.numCapturingParens += 1, true;
    e4.raise("Unterminated group");
  }
  return false;
}, Ih.regexp_eatExtendedAtom = function(e4) {
  return e4.eat(46) || this.regexp_eatReverseSolidusAtomEscape(e4) || this.regexp_eatCharacterClass(e4) || this.regexp_eatUncapturingGroup(e4) || this.regexp_eatCapturingGroup(e4) || this.regexp_eatInvalidBracedQuantifier(e4) || this.regexp_eatExtendedPatternCharacter(e4);
}, Ih.regexp_eatInvalidBracedQuantifier = function(e4) {
  return this.regexp_eatBracedQuantifier(e4, true) && e4.raise("Nothing to repeat"), false;
}, Ih.regexp_eatSyntaxCharacter = function(e4) {
  var t2 = e4.current();
  return !!Ph(t2) && (e4.lastIntValue = t2, e4.advance(), true);
}, Ih.regexp_eatPatternCharacters = function(e4) {
  for (var t2 = e4.pos, s2 = 0; -1 !== (s2 = e4.current()) && !Ph(s2); )
    e4.advance();
  return e4.pos !== t2;
}, Ih.regexp_eatExtendedPatternCharacter = function(e4) {
  var t2 = e4.current();
  return !(-1 === t2 || 36 === t2 || t2 >= 40 && t2 <= 43 || 46 === t2 || 63 === t2 || 91 === t2 || 94 === t2 || 124 === t2) && (e4.advance(), true);
}, Ih.regexp_groupSpecifier = function(e4) {
  if (e4.eat(63)) {
    if (this.regexp_eatGroupName(e4))
      return -1 !== e4.groupNames.indexOf(e4.lastStringValue) && e4.raise("Duplicate capture group name"), void e4.groupNames.push(e4.lastStringValue);
    e4.raise("Invalid group");
  }
}, Ih.regexp_eatGroupName = function(e4) {
  if (e4.lastStringValue = "", e4.eat(60)) {
    if (this.regexp_eatRegExpIdentifierName(e4) && e4.eat(62))
      return true;
    e4.raise("Invalid capture group name");
  }
  return false;
}, Ih.regexp_eatRegExpIdentifierName = function(e4) {
  if (e4.lastStringValue = "", this.regexp_eatRegExpIdentifierStart(e4)) {
    for (e4.lastStringValue += Pc(e4.lastIntValue); this.regexp_eatRegExpIdentifierPart(e4); )
      e4.lastStringValue += Pc(e4.lastIntValue);
    return true;
  }
  return false;
}, Ih.regexp_eatRegExpIdentifierStart = function(e4) {
  var t2 = e4.pos, s2 = this.options.ecmaVersion >= 11, i2 = e4.current(s2);
  return e4.advance(s2), 92 === i2 && this.regexp_eatRegExpUnicodeEscapeSequence(e4, s2) && (i2 = e4.lastIntValue), function(e5) {
    return oc(e5, true) || 36 === e5 || 95 === e5;
  }(i2) ? (e4.lastIntValue = i2, true) : (e4.pos = t2, false);
}, Ih.regexp_eatRegExpIdentifierPart = function(e4) {
  var t2 = e4.pos, s2 = this.options.ecmaVersion >= 11, i2 = e4.current(s2);
  return e4.advance(s2), 92 === i2 && this.regexp_eatRegExpUnicodeEscapeSequence(e4, s2) && (i2 = e4.lastIntValue), function(e5) {
    return ac(e5, true) || 36 === e5 || 95 === e5 || 8204 === e5 || 8205 === e5;
  }(i2) ? (e4.lastIntValue = i2, true) : (e4.pos = t2, false);
}, Ih.regexp_eatAtomEscape = function(e4) {
  return !!(this.regexp_eatBackReference(e4) || this.regexp_eatCharacterClassEscape(e4) || this.regexp_eatCharacterEscape(e4) || e4.switchN && this.regexp_eatKGroupName(e4)) || (e4.switchU && (99 === e4.current() && e4.raise("Invalid unicode escape"), e4.raise("Invalid escape")), false);
}, Ih.regexp_eatBackReference = function(e4) {
  var t2 = e4.pos;
  if (this.regexp_eatDecimalEscape(e4)) {
    var s2 = e4.lastIntValue;
    if (e4.switchU)
      return s2 > e4.maxBackReference && (e4.maxBackReference = s2), true;
    if (s2 <= e4.numCapturingParens)
      return true;
    e4.pos = t2;
  }
  return false;
}, Ih.regexp_eatKGroupName = function(e4) {
  if (e4.eat(107)) {
    if (this.regexp_eatGroupName(e4))
      return e4.backReferenceNames.push(e4.lastStringValue), true;
    e4.raise("Invalid named reference");
  }
  return false;
}, Ih.regexp_eatCharacterEscape = function(e4) {
  return this.regexp_eatControlEscape(e4) || this.regexp_eatCControlLetter(e4) || this.regexp_eatZero(e4) || this.regexp_eatHexEscapeSequence(e4) || this.regexp_eatRegExpUnicodeEscapeSequence(e4, false) || !e4.switchU && this.regexp_eatLegacyOctalEscapeSequence(e4) || this.regexp_eatIdentityEscape(e4);
}, Ih.regexp_eatCControlLetter = function(e4) {
  var t2 = e4.pos;
  if (e4.eat(99)) {
    if (this.regexp_eatControlLetter(e4))
      return true;
    e4.pos = t2;
  }
  return false;
}, Ih.regexp_eatZero = function(e4) {
  return 48 === e4.current() && !_h(e4.lookahead()) && (e4.lastIntValue = 0, e4.advance(), true);
}, Ih.regexp_eatControlEscape = function(e4) {
  var t2 = e4.current();
  return 116 === t2 ? (e4.lastIntValue = 9, e4.advance(), true) : 110 === t2 ? (e4.lastIntValue = 10, e4.advance(), true) : 118 === t2 ? (e4.lastIntValue = 11, e4.advance(), true) : 102 === t2 ? (e4.lastIntValue = 12, e4.advance(), true) : 114 === t2 && (e4.lastIntValue = 13, e4.advance(), true);
}, Ih.regexp_eatControlLetter = function(e4) {
  var t2 = e4.current();
  return !!Ch(t2) && (e4.lastIntValue = t2 % 32, e4.advance(), true);
}, Ih.regexp_eatRegExpUnicodeEscapeSequence = function(e4, t2) {
  void 0 === t2 && (t2 = false);
  var s2, i2 = e4.pos, n2 = t2 || e4.switchU;
  if (e4.eat(117)) {
    if (this.regexp_eatFixedHexDigits(e4, 4)) {
      var r2 = e4.lastIntValue;
      if (n2 && r2 >= 55296 && r2 <= 56319) {
        var o2 = e4.pos;
        if (e4.eat(92) && e4.eat(117) && this.regexp_eatFixedHexDigits(e4, 4)) {
          var a2 = e4.lastIntValue;
          if (a2 >= 56320 && a2 <= 57343)
            return e4.lastIntValue = 1024 * (r2 - 55296) + (a2 - 56320) + 65536, true;
        }
        e4.pos = o2, e4.lastIntValue = r2;
      }
      return true;
    }
    if (n2 && e4.eat(123) && this.regexp_eatHexDigits(e4) && e4.eat(125) && ((s2 = e4.lastIntValue) >= 0 && s2 <= 1114111))
      return true;
    n2 && e4.raise("Invalid unicode escape"), e4.pos = i2;
  }
  return false;
}, Ih.regexp_eatIdentityEscape = function(e4) {
  if (e4.switchU)
    return !!this.regexp_eatSyntaxCharacter(e4) || !!e4.eat(47) && (e4.lastIntValue = 47, true);
  var t2 = e4.current();
  return !(99 === t2 || e4.switchN && 107 === t2) && (e4.lastIntValue = t2, e4.advance(), true);
}, Ih.regexp_eatDecimalEscape = function(e4) {
  e4.lastIntValue = 0;
  var t2 = e4.current();
  if (t2 >= 49 && t2 <= 57) {
    do {
      e4.lastIntValue = 10 * e4.lastIntValue + (t2 - 48), e4.advance();
    } while ((t2 = e4.current()) >= 48 && t2 <= 57);
    return true;
  }
  return false;
};
function $h(e4) {
  return Ch(e4) || 95 === e4;
}
function Nh(e4) {
  return $h(e4) || _h(e4);
}
function _h(e4) {
  return e4 >= 48 && e4 <= 57;
}
function Rh(e4) {
  return e4 >= 48 && e4 <= 57 || e4 >= 65 && e4 <= 70 || e4 >= 97 && e4 <= 102;
}
function Mh(e4) {
  return e4 >= 65 && e4 <= 70 ? e4 - 65 + 10 : e4 >= 97 && e4 <= 102 ? e4 - 97 + 10 : e4 - 48;
}
function Oh(e4) {
  return e4 >= 48 && e4 <= 55;
}
Ih.regexp_eatCharacterClassEscape = function(e4) {
  var t2 = e4.current();
  if (function(e5) {
    return 100 === e5 || 68 === e5 || 115 === e5 || 83 === e5 || 119 === e5 || 87 === e5;
  }(t2))
    return e4.lastIntValue = -1, e4.advance(), 1;
  var s2 = false;
  if (e4.switchU && this.options.ecmaVersion >= 9 && ((s2 = 80 === t2) || 112 === t2)) {
    var i2;
    if (e4.lastIntValue = -1, e4.advance(), e4.eat(123) && (i2 = this.regexp_eatUnicodePropertyValueExpression(e4)) && e4.eat(125))
      return s2 && 2 === i2 && e4.raise("Invalid property name"), i2;
    e4.raise("Invalid property name");
  }
  return 0;
}, Ih.regexp_eatUnicodePropertyValueExpression = function(e4) {
  var t2 = e4.pos;
  if (this.regexp_eatUnicodePropertyName(e4) && e4.eat(61)) {
    var s2 = e4.lastStringValue;
    if (this.regexp_eatUnicodePropertyValue(e4)) {
      var i2 = e4.lastStringValue;
      return this.regexp_validateUnicodePropertyNameAndValue(e4, s2, i2), 1;
    }
  }
  if (e4.pos = t2, this.regexp_eatLoneUnicodePropertyNameOrValue(e4)) {
    var n2 = e4.lastStringValue;
    return this.regexp_validateUnicodePropertyNameOrValue(e4, n2);
  }
  return 0;
}, Ih.regexp_validateUnicodePropertyNameAndValue = function(e4, t2, s2) {
  kc(e4.unicodeProperties.nonBinary, t2) || e4.raise("Invalid property name"), e4.unicodeProperties.nonBinary[t2].test(s2) || e4.raise("Invalid property value");
}, Ih.regexp_validateUnicodePropertyNameOrValue = function(e4, t2) {
  return e4.unicodeProperties.binary.test(t2) ? 1 : e4.switchV && e4.unicodeProperties.binaryOfStrings.test(t2) ? 2 : void e4.raise("Invalid property name");
}, Ih.regexp_eatUnicodePropertyName = function(e4) {
  var t2 = 0;
  for (e4.lastStringValue = ""; $h(t2 = e4.current()); )
    e4.lastStringValue += Pc(t2), e4.advance();
  return "" !== e4.lastStringValue;
}, Ih.regexp_eatUnicodePropertyValue = function(e4) {
  var t2 = 0;
  for (e4.lastStringValue = ""; Nh(t2 = e4.current()); )
    e4.lastStringValue += Pc(t2), e4.advance();
  return "" !== e4.lastStringValue;
}, Ih.regexp_eatLoneUnicodePropertyNameOrValue = function(e4) {
  return this.regexp_eatUnicodePropertyValue(e4);
}, Ih.regexp_eatCharacterClass = function(e4) {
  if (e4.eat(91)) {
    var t2 = e4.eat(94), s2 = this.regexp_classContents(e4);
    return e4.eat(93) || e4.raise("Unterminated character class"), t2 && 2 === s2 && e4.raise("Negated character class may contain strings"), true;
  }
  return false;
}, Ih.regexp_classContents = function(e4) {
  return 93 === e4.current() ? 1 : e4.switchV ? this.regexp_classSetExpression(e4) : (this.regexp_nonEmptyClassRanges(e4), 1);
}, Ih.regexp_nonEmptyClassRanges = function(e4) {
  for (; this.regexp_eatClassAtom(e4); ) {
    var t2 = e4.lastIntValue;
    if (e4.eat(45) && this.regexp_eatClassAtom(e4)) {
      var s2 = e4.lastIntValue;
      !e4.switchU || -1 !== t2 && -1 !== s2 || e4.raise("Invalid character class"), -1 !== t2 && -1 !== s2 && t2 > s2 && e4.raise("Range out of order in character class");
    }
  }
}, Ih.regexp_eatClassAtom = function(e4) {
  var t2 = e4.pos;
  if (e4.eat(92)) {
    if (this.regexp_eatClassEscape(e4))
      return true;
    if (e4.switchU) {
      var s2 = e4.current();
      (99 === s2 || Oh(s2)) && e4.raise("Invalid class escape"), e4.raise("Invalid escape");
    }
    e4.pos = t2;
  }
  var i2 = e4.current();
  return 93 !== i2 && (e4.lastIntValue = i2, e4.advance(), true);
}, Ih.regexp_eatClassEscape = function(e4) {
  var t2 = e4.pos;
  if (e4.eat(98))
    return e4.lastIntValue = 8, true;
  if (e4.switchU && e4.eat(45))
    return e4.lastIntValue = 45, true;
  if (!e4.switchU && e4.eat(99)) {
    if (this.regexp_eatClassControlLetter(e4))
      return true;
    e4.pos = t2;
  }
  return this.regexp_eatCharacterClassEscape(e4) || this.regexp_eatCharacterEscape(e4);
}, Ih.regexp_classSetExpression = function(e4) {
  var t2, s2 = 1;
  if (this.regexp_eatClassSetRange(e4))
    ;
  else if (t2 = this.regexp_eatClassSetOperand(e4)) {
    2 === t2 && (s2 = 2);
    for (var i2 = e4.pos; e4.eatChars([38, 38]); )
      38 !== e4.current() && (t2 = this.regexp_eatClassSetOperand(e4)) ? 2 !== t2 && (s2 = 1) : e4.raise("Invalid character in character class");
    if (i2 !== e4.pos)
      return s2;
    for (; e4.eatChars([45, 45]); )
      this.regexp_eatClassSetOperand(e4) || e4.raise("Invalid character in character class");
    if (i2 !== e4.pos)
      return s2;
  } else
    e4.raise("Invalid character in character class");
  for (; ; )
    if (!this.regexp_eatClassSetRange(e4)) {
      if (!(t2 = this.regexp_eatClassSetOperand(e4)))
        return s2;
      2 === t2 && (s2 = 2);
    }
}, Ih.regexp_eatClassSetRange = function(e4) {
  var t2 = e4.pos;
  if (this.regexp_eatClassSetCharacter(e4)) {
    var s2 = e4.lastIntValue;
    if (e4.eat(45) && this.regexp_eatClassSetCharacter(e4)) {
      var i2 = e4.lastIntValue;
      return -1 !== s2 && -1 !== i2 && s2 > i2 && e4.raise("Range out of order in character class"), true;
    }
    e4.pos = t2;
  }
  return false;
}, Ih.regexp_eatClassSetOperand = function(e4) {
  return this.regexp_eatClassSetCharacter(e4) ? 1 : this.regexp_eatClassStringDisjunction(e4) || this.regexp_eatNestedClass(e4);
}, Ih.regexp_eatNestedClass = function(e4) {
  var t2 = e4.pos;
  if (e4.eat(91)) {
    var s2 = e4.eat(94), i2 = this.regexp_classContents(e4);
    if (e4.eat(93))
      return s2 && 2 === i2 && e4.raise("Negated character class may contain strings"), i2;
    e4.pos = t2;
  }
  if (e4.eat(92)) {
    var n2 = this.regexp_eatCharacterClassEscape(e4);
    if (n2)
      return n2;
    e4.pos = t2;
  }
  return null;
}, Ih.regexp_eatClassStringDisjunction = function(e4) {
  var t2 = e4.pos;
  if (e4.eatChars([92, 113])) {
    if (e4.eat(123)) {
      var s2 = this.regexp_classStringDisjunctionContents(e4);
      if (e4.eat(125))
        return s2;
    } else
      e4.raise("Invalid escape");
    e4.pos = t2;
  }
  return null;
}, Ih.regexp_classStringDisjunctionContents = function(e4) {
  for (var t2 = this.regexp_classString(e4); e4.eat(124); )
    2 === this.regexp_classString(e4) && (t2 = 2);
  return t2;
}, Ih.regexp_classString = function(e4) {
  for (var t2 = 0; this.regexp_eatClassSetCharacter(e4); )
    t2++;
  return 1 === t2 ? 1 : 2;
}, Ih.regexp_eatClassSetCharacter = function(e4) {
  var t2 = e4.pos;
  if (e4.eat(92))
    return !(!this.regexp_eatCharacterEscape(e4) && !this.regexp_eatClassSetReservedPunctuator(e4)) || (e4.eat(98) ? (e4.lastIntValue = 8, true) : (e4.pos = t2, false));
  var s2 = e4.current();
  return !(s2 < 0 || s2 === e4.lookahead() && function(e5) {
    return 33 === e5 || e5 >= 35 && e5 <= 38 || e5 >= 42 && e5 <= 44 || 46 === e5 || e5 >= 58 && e5 <= 64 || 94 === e5 || 96 === e5 || 126 === e5;
  }(s2)) && (!function(e5) {
    return 40 === e5 || 41 === e5 || 45 === e5 || 47 === e5 || e5 >= 91 && e5 <= 93 || e5 >= 123 && e5 <= 125;
  }(s2) && (e4.advance(), e4.lastIntValue = s2, true));
}, Ih.regexp_eatClassSetReservedPunctuator = function(e4) {
  var t2 = e4.current();
  return !!function(e5) {
    return 33 === e5 || 35 === e5 || 37 === e5 || 38 === e5 || 44 === e5 || 45 === e5 || e5 >= 58 && e5 <= 62 || 64 === e5 || 96 === e5 || 126 === e5;
  }(t2) && (e4.lastIntValue = t2, e4.advance(), true);
}, Ih.regexp_eatClassControlLetter = function(e4) {
  var t2 = e4.current();
  return !(!_h(t2) && 95 !== t2) && (e4.lastIntValue = t2 % 32, e4.advance(), true);
}, Ih.regexp_eatHexEscapeSequence = function(e4) {
  var t2 = e4.pos;
  if (e4.eat(120)) {
    if (this.regexp_eatFixedHexDigits(e4, 2))
      return true;
    e4.switchU && e4.raise("Invalid escape"), e4.pos = t2;
  }
  return false;
}, Ih.regexp_eatDecimalDigits = function(e4) {
  var t2 = e4.pos, s2 = 0;
  for (e4.lastIntValue = 0; _h(s2 = e4.current()); )
    e4.lastIntValue = 10 * e4.lastIntValue + (s2 - 48), e4.advance();
  return e4.pos !== t2;
}, Ih.regexp_eatHexDigits = function(e4) {
  var t2 = e4.pos, s2 = 0;
  for (e4.lastIntValue = 0; Rh(s2 = e4.current()); )
    e4.lastIntValue = 16 * e4.lastIntValue + Mh(s2), e4.advance();
  return e4.pos !== t2;
}, Ih.regexp_eatLegacyOctalEscapeSequence = function(e4) {
  if (this.regexp_eatOctalDigit(e4)) {
    var t2 = e4.lastIntValue;
    if (this.regexp_eatOctalDigit(e4)) {
      var s2 = e4.lastIntValue;
      t2 <= 3 && this.regexp_eatOctalDigit(e4) ? e4.lastIntValue = 64 * t2 + 8 * s2 + e4.lastIntValue : e4.lastIntValue = 8 * t2 + s2;
    } else
      e4.lastIntValue = t2;
    return true;
  }
  return false;
}, Ih.regexp_eatOctalDigit = function(e4) {
  var t2 = e4.current();
  return Oh(t2) ? (e4.lastIntValue = t2 - 48, e4.advance(), true) : (e4.lastIntValue = 0, false);
}, Ih.regexp_eatFixedHexDigits = function(e4, t2) {
  var s2 = e4.pos;
  e4.lastIntValue = 0;
  for (var i2 = 0; i2 < t2; ++i2) {
    var n2 = e4.current();
    if (!Rh(n2))
      return e4.pos = s2, false;
    e4.lastIntValue = 16 * e4.lastIntValue + Mh(n2), e4.advance();
  }
  return true;
};
var Dh = function(e4) {
  this.type = e4.type, this.value = e4.value, this.start = e4.start, this.end = e4.end, e4.options.locations && (this.loc = new Nc(e4, e4.startLoc, e4.endLoc)), e4.options.ranges && (this.range = [e4.start, e4.end]);
}, Lh = Tc.prototype;
function Th(e4) {
  return "function" != typeof BigInt ? null : BigInt(e4.replace(/_/g, ""));
}
Lh.next = function(e4) {
  !e4 && this.type.keyword && this.containsEsc && this.raiseRecoverable(this.start, "Escape sequence in keyword " + this.type.keyword), this.options.onToken && this.options.onToken(new Dh(this)), this.lastTokEnd = this.end, this.lastTokStart = this.start, this.lastTokEndLoc = this.endLoc, this.lastTokStartLoc = this.startLoc, this.nextToken();
}, Lh.getToken = function() {
  return this.next(), new Dh(this);
}, "undefined" != typeof Symbol && (Lh[Symbol.iterator] = function() {
  var e4 = this;
  return { next: function() {
    var t2 = e4.getToken();
    return { done: t2.type === fc.eof, value: t2 };
  } };
}), Lh.nextToken = function() {
  var e4 = this.curContext();
  return e4 && e4.preserveSpace || this.skipSpace(), this.start = this.pos, this.options.locations && (this.startLoc = this.curPosition()), this.pos >= this.input.length ? this.finishToken(fc.eof) : e4.override ? e4.override(this) : void this.readToken(this.fullCharCodeAtPos());
}, Lh.readToken = function(e4) {
  return oc(e4, this.options.ecmaVersion >= 6) || 92 === e4 ? this.readWord() : this.getTokenFromCode(e4);
}, Lh.fullCharCodeAtPos = function() {
  var e4 = this.input.charCodeAt(this.pos);
  if (e4 <= 55295 || e4 >= 56320)
    return e4;
  var t2 = this.input.charCodeAt(this.pos + 1);
  return t2 <= 56319 || t2 >= 57344 ? e4 : (e4 << 10) + t2 - 56613888;
}, Lh.skipBlockComment = function() {
  var e4 = this.options.onComment && this.curPosition(), t2 = this.pos, s2 = this.input.indexOf("*/", this.pos += 2);
  if (-1 === s2 && this.raise(this.pos - 2, "Unterminated comment"), this.pos = s2 + 2, this.options.locations)
    for (var i2 = void 0, n2 = t2; (i2 = xc(this.input, n2, this.pos)) > -1; )
      ++this.curLine, n2 = this.lineStart = i2;
  this.options.onComment && this.options.onComment(true, this.input.slice(t2 + 2, s2), t2, this.pos, e4, this.curPosition());
}, Lh.skipLineComment = function(e4) {
  for (var t2 = this.pos, s2 = this.options.onComment && this.curPosition(), i2 = this.input.charCodeAt(this.pos += e4); this.pos < this.input.length && !yc(i2); )
    i2 = this.input.charCodeAt(++this.pos);
  this.options.onComment && this.options.onComment(false, this.input.slice(t2 + e4, this.pos), t2, this.pos, s2, this.curPosition());
}, Lh.skipSpace = function() {
  e:
    for (; this.pos < this.input.length; ) {
      var e4 = this.input.charCodeAt(this.pos);
      switch (e4) {
        case 32:
        case 160:
          ++this.pos;
          break;
        case 13:
          10 === this.input.charCodeAt(this.pos + 1) && ++this.pos;
        case 10:
        case 8232:
        case 8233:
          ++this.pos, this.options.locations && (++this.curLine, this.lineStart = this.pos);
          break;
        case 47:
          switch (this.input.charCodeAt(this.pos + 1)) {
            case 42:
              this.skipBlockComment();
              break;
            case 47:
              this.skipLineComment(2);
              break;
            default:
              break e;
          }
          break;
        default:
          if (!(e4 > 8 && e4 < 14 || e4 >= 5760 && Ec.test(String.fromCharCode(e4))))
            break e;
          ++this.pos;
      }
    }
}, Lh.finishToken = function(e4, t2) {
  this.end = this.pos, this.options.locations && (this.endLoc = this.curPosition());
  var s2 = this.type;
  this.type = e4, this.value = t2, this.updateContext(s2);
}, Lh.readToken_dot = function() {
  var e4 = this.input.charCodeAt(this.pos + 1);
  if (e4 >= 48 && e4 <= 57)
    return this.readNumber(true);
  var t2 = this.input.charCodeAt(this.pos + 2);
  return this.options.ecmaVersion >= 6 && 46 === e4 && 46 === t2 ? (this.pos += 3, this.finishToken(fc.ellipsis)) : (++this.pos, this.finishToken(fc.dot));
}, Lh.readToken_slash = function() {
  var e4 = this.input.charCodeAt(this.pos + 1);
  return this.exprAllowed ? (++this.pos, this.readRegexp()) : 61 === e4 ? this.finishOp(fc.assign, 2) : this.finishOp(fc.slash, 1);
}, Lh.readToken_mult_modulo_exp = function(e4) {
  var t2 = this.input.charCodeAt(this.pos + 1), s2 = 1, i2 = 42 === e4 ? fc.star : fc.modulo;
  return this.options.ecmaVersion >= 7 && 42 === e4 && 42 === t2 && (++s2, i2 = fc.starstar, t2 = this.input.charCodeAt(this.pos + 2)), 61 === t2 ? this.finishOp(fc.assign, s2 + 1) : this.finishOp(i2, s2);
}, Lh.readToken_pipe_amp = function(e4) {
  var t2 = this.input.charCodeAt(this.pos + 1);
  if (t2 === e4) {
    if (this.options.ecmaVersion >= 12) {
      if (61 === this.input.charCodeAt(this.pos + 2))
        return this.finishOp(fc.assign, 3);
    }
    return this.finishOp(124 === e4 ? fc.logicalOR : fc.logicalAND, 2);
  }
  return 61 === t2 ? this.finishOp(fc.assign, 2) : this.finishOp(124 === e4 ? fc.bitwiseOR : fc.bitwiseAND, 1);
}, Lh.readToken_caret = function() {
  return 61 === this.input.charCodeAt(this.pos + 1) ? this.finishOp(fc.assign, 2) : this.finishOp(fc.bitwiseXOR, 1);
}, Lh.readToken_plus_min = function(e4) {
  var t2 = this.input.charCodeAt(this.pos + 1);
  return t2 === e4 ? 45 !== t2 || this.inModule || 62 !== this.input.charCodeAt(this.pos + 2) || 0 !== this.lastTokEnd && !mc.test(this.input.slice(this.lastTokEnd, this.pos)) ? this.finishOp(fc.incDec, 2) : (this.skipLineComment(3), this.skipSpace(), this.nextToken()) : 61 === t2 ? this.finishOp(fc.assign, 2) : this.finishOp(fc.plusMin, 1);
}, Lh.readToken_lt_gt = function(e4) {
  var t2 = this.input.charCodeAt(this.pos + 1), s2 = 1;
  return t2 === e4 ? (s2 = 62 === e4 && 62 === this.input.charCodeAt(this.pos + 2) ? 3 : 2, 61 === this.input.charCodeAt(this.pos + s2) ? this.finishOp(fc.assign, s2 + 1) : this.finishOp(fc.bitShift, s2)) : 33 !== t2 || 60 !== e4 || this.inModule || 45 !== this.input.charCodeAt(this.pos + 2) || 45 !== this.input.charCodeAt(this.pos + 3) ? (61 === t2 && (s2 = 2), this.finishOp(fc.relational, s2)) : (this.skipLineComment(4), this.skipSpace(), this.nextToken());
}, Lh.readToken_eq_excl = function(e4) {
  var t2 = this.input.charCodeAt(this.pos + 1);
  return 61 === t2 ? this.finishOp(fc.equality, 61 === this.input.charCodeAt(this.pos + 2) ? 3 : 2) : 61 === e4 && 62 === t2 && this.options.ecmaVersion >= 6 ? (this.pos += 2, this.finishToken(fc.arrow)) : this.finishOp(61 === e4 ? fc.eq : fc.prefix, 1);
}, Lh.readToken_question = function() {
  var e4 = this.options.ecmaVersion;
  if (e4 >= 11) {
    var t2 = this.input.charCodeAt(this.pos + 1);
    if (46 === t2) {
      var s2 = this.input.charCodeAt(this.pos + 2);
      if (s2 < 48 || s2 > 57)
        return this.finishOp(fc.questionDot, 2);
    }
    if (63 === t2) {
      if (e4 >= 12) {
        if (61 === this.input.charCodeAt(this.pos + 2))
          return this.finishOp(fc.assign, 3);
      }
      return this.finishOp(fc.coalesce, 2);
    }
  }
  return this.finishOp(fc.question, 1);
}, Lh.readToken_numberSign = function() {
  var e4 = 35;
  if (this.options.ecmaVersion >= 13 && (++this.pos, oc(e4 = this.fullCharCodeAtPos(), true) || 92 === e4))
    return this.finishToken(fc.privateId, this.readWord1());
  this.raise(this.pos, "Unexpected character '" + Pc(e4) + "'");
}, Lh.getTokenFromCode = function(e4) {
  switch (e4) {
    case 46:
      return this.readToken_dot();
    case 40:
      return ++this.pos, this.finishToken(fc.parenL);
    case 41:
      return ++this.pos, this.finishToken(fc.parenR);
    case 59:
      return ++this.pos, this.finishToken(fc.semi);
    case 44:
      return ++this.pos, this.finishToken(fc.comma);
    case 91:
      return ++this.pos, this.finishToken(fc.bracketL);
    case 93:
      return ++this.pos, this.finishToken(fc.bracketR);
    case 123:
      return ++this.pos, this.finishToken(fc.braceL);
    case 125:
      return ++this.pos, this.finishToken(fc.braceR);
    case 58:
      return ++this.pos, this.finishToken(fc.colon);
    case 96:
      if (this.options.ecmaVersion < 6)
        break;
      return ++this.pos, this.finishToken(fc.backQuote);
    case 48:
      var t2 = this.input.charCodeAt(this.pos + 1);
      if (120 === t2 || 88 === t2)
        return this.readRadixNumber(16);
      if (this.options.ecmaVersion >= 6) {
        if (111 === t2 || 79 === t2)
          return this.readRadixNumber(8);
        if (98 === t2 || 66 === t2)
          return this.readRadixNumber(2);
      }
    case 49:
    case 50:
    case 51:
    case 52:
    case 53:
    case 54:
    case 55:
    case 56:
    case 57:
      return this.readNumber(false);
    case 34:
    case 39:
      return this.readString(e4);
    case 47:
      return this.readToken_slash();
    case 37:
    case 42:
      return this.readToken_mult_modulo_exp(e4);
    case 124:
    case 38:
      return this.readToken_pipe_amp(e4);
    case 94:
      return this.readToken_caret();
    case 43:
    case 45:
      return this.readToken_plus_min(e4);
    case 60:
    case 62:
      return this.readToken_lt_gt(e4);
    case 61:
    case 33:
      return this.readToken_eq_excl(e4);
    case 63:
      return this.readToken_question();
    case 126:
      return this.finishOp(fc.prefix, 1);
    case 35:
      return this.readToken_numberSign();
  }
  this.raise(this.pos, "Unexpected character '" + Pc(e4) + "'");
}, Lh.finishOp = function(e4, t2) {
  var s2 = this.input.slice(this.pos, this.pos + t2);
  return this.pos += t2, this.finishToken(e4, s2);
}, Lh.readRegexp = function() {
  for (var e4, t2, s2 = this.pos; ; ) {
    this.pos >= this.input.length && this.raise(s2, "Unterminated regular expression");
    var i2 = this.input.charAt(this.pos);
    if (mc.test(i2) && this.raise(s2, "Unterminated regular expression"), e4)
      e4 = false;
    else {
      if ("[" === i2)
        t2 = true;
      else if ("]" === i2 && t2)
        t2 = false;
      else if ("/" === i2 && !t2)
        break;
      e4 = "\\" === i2;
    }
    ++this.pos;
  }
  var n2 = this.input.slice(s2, this.pos);
  ++this.pos;
  var r2 = this.pos, o2 = this.readWord1();
  this.containsEsc && this.unexpected(r2);
  var a2 = this.regexpState || (this.regexpState = new wh(this));
  a2.reset(s2, n2, o2), this.validateRegExpFlags(a2), this.validateRegExpPattern(a2);
  var l2 = null;
  try {
    l2 = new RegExp(n2, o2);
  } catch (e5) {
  }
  return this.finishToken(fc.regexp, { pattern: n2, flags: o2, value: l2 });
}, Lh.readInt = function(e4, t2, s2) {
  for (var i2 = this.options.ecmaVersion >= 12 && void 0 === t2, n2 = s2 && 48 === this.input.charCodeAt(this.pos), r2 = this.pos, o2 = 0, a2 = 0, l2 = 0, c2 = null == t2 ? 1 / 0 : t2; l2 < c2; ++l2, ++this.pos) {
    var h2 = this.input.charCodeAt(this.pos), u2 = void 0;
    if (i2 && 95 === h2)
      n2 && this.raiseRecoverable(this.pos, "Numeric separator is not allowed in legacy octal numeric literals"), 95 === a2 && this.raiseRecoverable(this.pos, "Numeric separator must be exactly one underscore"), 0 === l2 && this.raiseRecoverable(this.pos, "Numeric separator is not allowed at the first of digits"), a2 = h2;
    else {
      if ((u2 = h2 >= 97 ? h2 - 97 + 10 : h2 >= 65 ? h2 - 65 + 10 : h2 >= 48 && h2 <= 57 ? h2 - 48 : 1 / 0) >= e4)
        break;
      a2 = h2, o2 = o2 * e4 + u2;
    }
  }
  return i2 && 95 === a2 && this.raiseRecoverable(this.pos - 1, "Numeric separator is not allowed at the last of digits"), this.pos === r2 || null != t2 && this.pos - r2 !== t2 ? null : o2;
}, Lh.readRadixNumber = function(e4) {
  var t2 = this.pos;
  this.pos += 2;
  var s2 = this.readInt(e4);
  return null == s2 && this.raise(this.start + 2, "Expected number in radix " + e4), this.options.ecmaVersion >= 11 && 110 === this.input.charCodeAt(this.pos) ? (s2 = Th(this.input.slice(t2, this.pos)), ++this.pos) : oc(this.fullCharCodeAtPos()) && this.raise(this.pos, "Identifier directly after number"), this.finishToken(fc.num, s2);
}, Lh.readNumber = function(e4) {
  var t2 = this.pos;
  e4 || null !== this.readInt(10, void 0, true) || this.raise(t2, "Invalid number");
  var s2 = this.pos - t2 >= 2 && 48 === this.input.charCodeAt(t2);
  s2 && this.strict && this.raise(t2, "Invalid number");
  var i2 = this.input.charCodeAt(this.pos);
  if (!s2 && !e4 && this.options.ecmaVersion >= 11 && 110 === i2) {
    var n2 = Th(this.input.slice(t2, this.pos));
    return ++this.pos, oc(this.fullCharCodeAtPos()) && this.raise(this.pos, "Identifier directly after number"), this.finishToken(fc.num, n2);
  }
  s2 && /[89]/.test(this.input.slice(t2, this.pos)) && (s2 = false), 46 !== i2 || s2 || (++this.pos, this.readInt(10), i2 = this.input.charCodeAt(this.pos)), 69 !== i2 && 101 !== i2 || s2 || (43 !== (i2 = this.input.charCodeAt(++this.pos)) && 45 !== i2 || ++this.pos, null === this.readInt(10) && this.raise(t2, "Invalid number")), oc(this.fullCharCodeAtPos()) && this.raise(this.pos, "Identifier directly after number");
  var r2, o2 = (r2 = this.input.slice(t2, this.pos), s2 ? parseInt(r2, 8) : parseFloat(r2.replace(/_/g, "")));
  return this.finishToken(fc.num, o2);
}, Lh.readCodePoint = function() {
  var e4;
  if (123 === this.input.charCodeAt(this.pos)) {
    this.options.ecmaVersion < 6 && this.unexpected();
    var t2 = ++this.pos;
    e4 = this.readHexChar(this.input.indexOf("}", this.pos) - this.pos), ++this.pos, e4 > 1114111 && this.invalidStringToken(t2, "Code point out of bounds");
  } else
    e4 = this.readHexChar(4);
  return e4;
}, Lh.readString = function(e4) {
  for (var t2 = "", s2 = ++this.pos; ; ) {
    this.pos >= this.input.length && this.raise(this.start, "Unterminated string constant");
    var i2 = this.input.charCodeAt(this.pos);
    if (i2 === e4)
      break;
    92 === i2 ? (t2 += this.input.slice(s2, this.pos), t2 += this.readEscapedChar(false), s2 = this.pos) : 8232 === i2 || 8233 === i2 ? (this.options.ecmaVersion < 10 && this.raise(this.start, "Unterminated string constant"), ++this.pos, this.options.locations && (this.curLine++, this.lineStart = this.pos)) : (yc(i2) && this.raise(this.start, "Unterminated string constant"), ++this.pos);
  }
  return t2 += this.input.slice(s2, this.pos++), this.finishToken(fc.string, t2);
};
var Vh = {};
Lh.tryReadTemplateToken = function() {
  this.inTemplateElement = true;
  try {
    this.readTmplToken();
  } catch (e4) {
    if (e4 !== Vh)
      throw e4;
    this.readInvalidTemplateToken();
  }
  this.inTemplateElement = false;
}, Lh.invalidStringToken = function(e4, t2) {
  if (this.inTemplateElement && this.options.ecmaVersion >= 9)
    throw Vh;
  this.raise(e4, t2);
}, Lh.readTmplToken = function() {
  for (var e4 = "", t2 = this.pos; ; ) {
    this.pos >= this.input.length && this.raise(this.start, "Unterminated template");
    var s2 = this.input.charCodeAt(this.pos);
    if (96 === s2 || 36 === s2 && 123 === this.input.charCodeAt(this.pos + 1))
      return this.pos !== this.start || this.type !== fc.template && this.type !== fc.invalidTemplate ? (e4 += this.input.slice(t2, this.pos), this.finishToken(fc.template, e4)) : 36 === s2 ? (this.pos += 2, this.finishToken(fc.dollarBraceL)) : (++this.pos, this.finishToken(fc.backQuote));
    if (92 === s2)
      e4 += this.input.slice(t2, this.pos), e4 += this.readEscapedChar(true), t2 = this.pos;
    else if (yc(s2)) {
      switch (e4 += this.input.slice(t2, this.pos), ++this.pos, s2) {
        case 13:
          10 === this.input.charCodeAt(this.pos) && ++this.pos;
        case 10:
          e4 += "\n";
          break;
        default:
          e4 += String.fromCharCode(s2);
      }
      this.options.locations && (++this.curLine, this.lineStart = this.pos), t2 = this.pos;
    } else
      ++this.pos;
  }
}, Lh.readInvalidTemplateToken = function() {
  for (; this.pos < this.input.length; this.pos++)
    switch (this.input[this.pos]) {
      case "\\":
        ++this.pos;
        break;
      case "$":
        if ("{" !== this.input[this.pos + 1])
          break;
      case "`":
        return this.finishToken(fc.invalidTemplate, this.input.slice(this.start, this.pos));
    }
  this.raise(this.start, "Unterminated template");
}, Lh.readEscapedChar = function(e4) {
  var t2 = this.input.charCodeAt(++this.pos);
  switch (++this.pos, t2) {
    case 110:
      return "\n";
    case 114:
      return "\r";
    case 120:
      return String.fromCharCode(this.readHexChar(2));
    case 117:
      return Pc(this.readCodePoint());
    case 116:
      return "	";
    case 98:
      return "\b";
    case 118:
      return "\v";
    case 102:
      return "\f";
    case 13:
      10 === this.input.charCodeAt(this.pos) && ++this.pos;
    case 10:
      return this.options.locations && (this.lineStart = this.pos, ++this.curLine), "";
    case 56:
    case 57:
      if (this.strict && this.invalidStringToken(this.pos - 1, "Invalid escape sequence"), e4) {
        var s2 = this.pos - 1;
        this.invalidStringToken(s2, "Invalid escape sequence in template string");
      }
    default:
      if (t2 >= 48 && t2 <= 55) {
        var i2 = this.input.substr(this.pos - 1, 3).match(/^[0-7]+/)[0], n2 = parseInt(i2, 8);
        return n2 > 255 && (i2 = i2.slice(0, -1), n2 = parseInt(i2, 8)), this.pos += i2.length - 1, t2 = this.input.charCodeAt(this.pos), "0" === i2 && 56 !== t2 && 57 !== t2 || !this.strict && !e4 || this.invalidStringToken(this.pos - 1 - i2.length, e4 ? "Octal literal in template string" : "Octal literal in strict mode"), String.fromCharCode(n2);
      }
      return yc(t2) ? "" : String.fromCharCode(t2);
  }
}, Lh.readHexChar = function(e4) {
  var t2 = this.pos, s2 = this.readInt(16, e4);
  return null === s2 && this.invalidStringToken(t2, "Bad character escape sequence"), s2;
}, Lh.readWord1 = function() {
  this.containsEsc = false;
  for (var e4 = "", t2 = true, s2 = this.pos, i2 = this.options.ecmaVersion >= 6; this.pos < this.input.length; ) {
    var n2 = this.fullCharCodeAtPos();
    if (ac(n2, i2))
      this.pos += n2 <= 65535 ? 1 : 2;
    else {
      if (92 !== n2)
        break;
      this.containsEsc = true, e4 += this.input.slice(s2, this.pos);
      var r2 = this.pos;
      117 !== this.input.charCodeAt(++this.pos) && this.invalidStringToken(this.pos, "Expecting Unicode escape sequence \\uXXXX"), ++this.pos;
      var o2 = this.readCodePoint();
      (t2 ? oc : ac)(o2, i2) || this.invalidStringToken(r2, "Invalid Unicode escape"), e4 += Pc(o2), s2 = this.pos;
    }
    t2 = false;
  }
  return e4 + this.input.slice(s2, this.pos);
}, Lh.readWord = function() {
  var e4 = this.readWord1(), t2 = fc.name;
  return this.keywords.test(e4) && (t2 = dc[e4]), this.finishToken(t2, e4);
};
var Bh = "8.10.0";
Tc.acorn = { Parser: Tc, version: Bh, defaultOptions: Rc, Position: $c, SourceLocation: Nc, getLineInfo: _c, Node: oh, TokenType: lc, tokTypes: fc, keywordTypes: dc, TokContext: Qc, tokContexts: Zc, isIdentifierChar: ac, isIdentifierStart: oc, Token: Dh, isNewLine: yc, lineBreak: mc, lineBreakG: gc, nonASCIIwhitespace: Ec };
var zh = Object.freeze({ __proto__: null, Node: oh, Parser: Tc, Position: $c, SourceLocation: Nc, TokContext: Qc, Token: Dh, TokenType: lc, defaultOptions: Rc, getLineInfo: _c, isIdentifierChar: ac, isIdentifierStart: oc, isNewLine: yc, keywordTypes: dc, lineBreak: mc, lineBreakG: gc, nonASCIIwhitespace: Ec, parse: function(e4, t2) {
  return Tc.parse(e4, t2);
}, parseExpressionAt: function(e4, t2, s2) {
  return Tc.parseExpressionAt(e4, t2, s2);
}, tokContexts: Zc, tokTypes: fc, tokenizer: function(e4, t2) {
  return Tc.tokenizer(e4, t2);
}, version: Bh });
const Fh = (e4) => () => Qe(function(e5) {
  return { code: "NO_FS_IN_BROWSER", message: `Cannot access the file system (via "${e5}") when using the browser build of Rollup. Make sure you supply a plugin with custom resolveId and load hooks to Rollup.`, url: De("plugin-development/#a-simple-example") };
}(e4)), jh = Fh("fs.mkdir"), Uh = Fh("fs.readFile"), Gh = Fh("fs.writeFile");
async function Wh(e4, t2, s2, i2, n2, r2, o2, a2, l2) {
  const c2 = await function(e5, t3, s3, i3, n3, r3, o3, a3) {
    let l3 = null, c3 = null;
    if (n3) {
      l3 = /* @__PURE__ */ new Set();
      for (const s4 of n3)
        e5 === s4.source && t3 === s4.importer && l3.add(s4.plugin);
      c3 = (e6, t4) => ({ ...e6, resolve: (e7, s4, { assertions: r4, custom: o4, isEntry: a4, skipSelf: l4 } = me) => i3(e7, s4, o4, a4, r4 || ge, l4 ? [...n3, { importer: s4, plugin: t4, source: e7 }] : n3) });
    }
    return s3.hookFirstAndGetPlugin("resolveId", [e5, t3, { assertions: a3, custom: r3, isEntry: o3 }], c3, l3);
  }(e4, t2, i2, n2, r2, o2, a2, l2);
  return null == c2 ? Fh("path.resolve")() : c2[0];
}
const qh = "at position ", Hh = "at output position ";
const Kh = { delete: () => false, get() {
}, has: () => false, set() {
} };
function Yh(e4) {
  return e4.startsWith(qh) || e4.startsWith(Hh) ? Qe({ code: tt, message: "A plugin is trying to use the Rollup cache but is not declaring a plugin name or cacheKey." }) : Qe({ code: at, message: `The plugin name ${e4} is being used twice in the same build. Plugin names must be distinct or provide a cacheKey (please post an issue to the plugin if you are a plugin user).` });
}
const Xh = (e4, t2, s2 = tu) => {
  const { onwarn: i2, onLog: n2 } = e4, r2 = Qh(s2, i2);
  if (n2) {
    const e5 = we[t2];
    return (t3, s3) => n2(t3, Zh(s3), (t4, s4) => {
      if ("error" === t4)
        return Qe(Jh(s4));
      we[t4] >= e5 && r2(t4, Jh(s4));
    });
  }
  return r2;
}, Qh = (e4, t2) => t2 ? (s2, i2) => {
  s2 === Ae ? t2(Zh(i2), (t3) => e4(Ae, Jh(t3))) : e4(s2, i2);
} : e4, Zh = (e4) => (Object.defineProperty(e4, "toString", { value: () => eu(e4), writable: true }), e4), Jh = (e4) => "string" == typeof e4 ? { message: e4 } : "function" == typeof e4 ? Jh(e4()) : e4, eu = (e4) => {
  let t2 = "";
  return e4.plugin && (t2 += `(${e4.plugin} plugin) `), e4.loc && (t2 += `${V(e4.loc.file)} (${e4.loc.line}:${e4.loc.column}) `), t2 + e4.message;
}, tu = (e4, t2) => {
  const s2 = eu(t2);
  switch (e4) {
    case Ae:
      return console.warn(s2);
    case Ie:
      return console.debug(s2);
    default:
      return console.info(s2);
  }
};
function su(e4, t2, s2, i2, n2 = /$./) {
  const r2 = new Set(t2), o2 = Object.keys(e4).filter((e5) => !(r2.has(e5) || n2.test(e5)));
  o2.length > 0 && i2(Ae, function(e5, t3, s3) {
    return { code: $t, message: `Unknown ${e5}: ${t3.join(", ")}. Allowed options: ${s3.join(", ")}` };
  }(s2, o2, [...r2].sort()));
}
const iu = { recommended: { annotations: true, correctVarValueBeforeDeclaration: false, manualPureFunctions: ye, moduleSideEffects: () => true, propertyReadSideEffects: true, tryCatchDeoptimization: true, unknownGlobalSideEffects: false }, safest: { annotations: true, correctVarValueBeforeDeclaration: true, manualPureFunctions: ye, moduleSideEffects: () => true, propertyReadSideEffects: true, tryCatchDeoptimization: true, unknownGlobalSideEffects: true }, smallest: { annotations: true, correctVarValueBeforeDeclaration: false, manualPureFunctions: ye, moduleSideEffects: () => false, propertyReadSideEffects: false, tryCatchDeoptimization: false, unknownGlobalSideEffects: false } }, nu = { es2015: { arrowFunctions: true, constBindings: true, objectShorthand: true, reservedNamesAsProps: true, symbols: true }, es5: { arrowFunctions: false, constBindings: false, objectShorthand: false, reservedNamesAsProps: true, symbols: false } }, ru = (e4, t2, s2, i2, n2) => {
  const r2 = e4 == null ? void 0 : e4.preset;
  if (r2) {
    const n3 = t2[r2];
    if (n3)
      return { ...n3, ...e4 };
    Qe(jt(`${s2}.preset`, i2, `valid values are ${Oe(Object.keys(t2))}`, r2));
  }
  return ((e5, t3, s3, i3) => (n3) => {
    if ("string" == typeof n3) {
      const r3 = e5[n3];
      if (r3)
        return r3;
      Qe(jt(t3, s3, `valid values are ${i3}${Oe(Object.keys(e5))}. You can also supply an object for more fine-grained control`, n3));
    }
    return ((e6) => e6 && "object" == typeof e6 ? e6 : {})(n3);
  })(t2, s2, i2, n2)(e4);
}, ou = async (e4) => (await async function(e5) {
  do {
    e5 = (await Promise.all(e5)).flat(1 / 0);
  } while (e5.some((e6) => e6 == null ? void 0 : e6.then));
  return e5;
}([e4])).filter(Boolean);
async function au(e4, t2, s2, i2) {
  const n2 = t2.id, r2 = [];
  let o2 = null === e4.map ? null : Ao(e4.map);
  const a2 = e4.code;
  let c2 = e4.ast;
  const h2 = [], u2 = [];
  let d2 = false;
  const p2 = () => d2 = true;
  let f2 = "", m2 = e4.code;
  const g2 = (e5) => (t3, s3) => {
    t3 = Jh(t3), s3 && Ze(t3, s3, m2, n2), t3.id = n2, t3.hook = "transform", e5(t3);
  };
  let x2;
  try {
    x2 = await s2.hookReduceArg0("transform", [m2, n2], function(e5, s3, n3) {
      let o3, a3;
      if ("string" == typeof s3)
        o3 = s3;
      else {
        if (!s3 || "object" != typeof s3)
          return e5;
        if (t2.updateOptions(s3), null == s3.code)
          return (s3.map || s3.ast) && i2(Ae, function(e6) {
            return { code: kt, message: `The plugin "${e6}" returned a "map" or "ast" without returning a "code". This will be ignored.` };
          }(n3.name)), e5;
        ({ code: o3, map: a3, ast: c2 } = s3);
      }
      return null !== a3 && r2.push(Ao("string" == typeof a3 ? JSON.parse(a3) : a3) || { missing: true, plugin: n3.name }), m2 = o3, o3;
    }, (e5, t3) => {
      return f2 = t3.name, { ...e5, addWatchFile(t4) {
        h2.push(t4), e5.addWatchFile(t4);
      }, cache: d2 ? e5.cache : (c3 = e5.cache, x3 = p2, { delete: (e6) => (x3(), c3.delete(e6)), get: (e6) => (x3(), c3.get(e6)), has: (e6) => (x3(), c3.has(e6)), set: (e6, t4) => (x3(), c3.set(e6, t4)) }), debug: g2(e5.debug), emitFile: (e6) => (u2.push(e6), s2.emitFile(e6)), error: (t4, s3) => ("string" == typeof t4 && (t4 = { message: t4 }), s3 && Ze(t4, s3, m2, n2), t4.id = n2, t4.hook = "transform", e5.error(t4)), getCombinedSourcemap() {
        const e6 = function(e7, t4, s3, i3, n3) {
          return 0 === i3.length ? s3 : Ao({ version: 3, ...rl(e7, t4, s3, i3, nl(n3)).traceMappings() });
        }(n2, a2, o2, r2, i2);
        if (!e6) {
          return new y(a2).generateMap({ hires: true, includeContent: true, source: n2 });
        }
        return o2 !== e6 && (o2 = e6, r2.length = 0), new l({ ...e6, file: null, sourcesContent: e6.sourcesContent });
      }, info: g2(e5.info), setAssetSource() {
        return this.error({ code: gt, message: "setAssetSource cannot be called in transform for caching reasons. Use emitFile with a source, or call setAssetSource in another hook." });
      }, warn: g2(e5.warn) };
      var c3, x3;
    });
  } catch (e5) {
    return Qe(qt(e5, f2, { hook: "transform", id: n2 }));
  }
  return !d2 && u2.length > 0 && (t2.transformFiles = u2), { ast: c2, code: x2, customTransformCache: d2, originalCode: a2, originalSourcemap: o2, sourcemapChain: r2, transformDependencies: h2 };
}
const lu = "resolveDependencies";
class cu {
  constructor(e4, t2, s2, i2) {
    this.graph = e4, this.modulesById = t2, this.options = s2, this.pluginDriver = i2, this.implicitEntryModules = /* @__PURE__ */ new Set(), this.indexedEntryModules = [], this.latestLoadModulesPromise = Promise.resolve(), this.moduleLoadPromises = /* @__PURE__ */ new Map(), this.modulesWithLoadedDependencies = /* @__PURE__ */ new Set(), this.nextChunkNamePriority = 0, this.nextEntryModuleIndex = 0, this.resolveId = async (e5, t3, s3, i3, n2, r2 = null) => this.getResolvedIdWithDefaults(this.getNormalizedResolvedIdWithoutDefaults(!this.options.external(e5, t3, false) && await Wh(e5, t3, this.options.preserveSymlinks, this.pluginDriver, this.resolveId, r2, s3, "boolean" == typeof i3 ? i3 : !t3, n2), t3, e5), n2), this.hasModuleSideEffects = s2.treeshake ? s2.treeshake.moduleSideEffects : () => true;
  }
  async addAdditionalModules(e4, t2) {
    const s2 = this.extendLoadModulesPromise(Promise.all(e4.map((e5) => this.loadEntryModule(e5, false, void 0, null, t2))));
    return await this.awaitLoadModulesPromise(), s2;
  }
  async addEntryModules(e4, t2) {
    const s2 = this.nextEntryModuleIndex;
    this.nextEntryModuleIndex += e4.length;
    const i2 = this.nextChunkNamePriority;
    this.nextChunkNamePriority += e4.length;
    const n2 = await this.extendLoadModulesPromise(Promise.all(e4.map(({ id: e5, importer: t3 }) => this.loadEntryModule(e5, true, t3, null))).then((n3) => {
      for (const [r2, o2] of n3.entries()) {
        o2.isUserDefinedEntryPoint = o2.isUserDefinedEntryPoint || t2, uu(o2, e4[r2], t2, i2 + r2);
        const n4 = this.indexedEntryModules.find((e5) => e5.module === o2);
        n4 ? n4.index = Math.min(n4.index, s2 + r2) : this.indexedEntryModules.push({ index: s2 + r2, module: o2 });
      }
      return this.indexedEntryModules.sort(({ index: e5 }, { index: t3 }) => e5 > t3 ? 1 : -1), n3;
    }));
    return await this.awaitLoadModulesPromise(), { entryModules: this.indexedEntryModules.map(({ module: e5 }) => e5), implicitEntryModules: [...this.implicitEntryModules], newEntryModules: n2 };
  }
  async emitChunk({ fileName: e4, id: t2, importer: s2, name: i2, implicitlyLoadedAfterOneOf: n2, preserveSignature: r2 }) {
    const o2 = { fileName: e4 || null, id: t2, importer: s2, name: i2 || null }, a2 = n2 ? await this.addEntryWithImplicitDependants(o2, n2) : (await this.addEntryModules([o2], false)).newEntryModules[0];
    return null != r2 && (a2.preserveSignature = r2), a2;
  }
  async preloadModule(e4) {
    return (await this.fetchModule(this.getResolvedIdWithDefaults(e4, ge), void 0, false, !e4.resolveDependencies || lu)).info;
  }
  addEntryWithImplicitDependants(e4, t2) {
    const s2 = this.nextChunkNamePriority++;
    return this.extendLoadModulesPromise(this.loadEntryModule(e4.id, false, e4.importer, null).then(async (i2) => {
      if (uu(i2, e4, false, s2), !i2.info.isEntry) {
        this.implicitEntryModules.add(i2);
        const s3 = await Promise.all(t2.map((t3) => this.loadEntryModule(t3, false, e4.importer, i2.id)));
        for (const e5 of s3)
          i2.implicitlyLoadedAfter.add(e5);
        for (const e5 of i2.implicitlyLoadedAfter)
          e5.implicitlyLoadedBefore.add(i2);
      }
      return i2;
    }));
  }
  async addModuleSource(e4, t2, s2) {
    let i2;
    try {
      i2 = await this.graph.fileOperationQueue.run(async () => await this.pluginDriver.hookFirst("load", [e4]) ?? await Uh(e4, "utf8"));
    } catch (s3) {
      let i3 = `Could not load ${e4}`;
      throw t2 && (i3 += ` (imported by ${V(t2)})`), i3 += `: ${s3.message}`, s3.message = i3, s3;
    }
    const n2 = "string" == typeof i2 ? { code: i2 } : null != i2 && "object" == typeof i2 && "string" == typeof i2.code ? i2 : Qe(function(e5) {
      return { code: "BAD_LOADER", message: `Error loading "${V(e5)}": plugin load hook should return a string, a { code, map } object, or nothing/null.` };
    }(e4)), r2 = this.graph.cachedModules.get(e4);
    if (!r2 || r2.customTransformCache || r2.originalCode !== n2.code || await this.pluginDriver.hookFirst("shouldTransformCachedModule", [{ ast: r2.ast, code: r2.code, id: r2.id, meta: r2.meta, moduleSideEffects: r2.moduleSideEffects, resolvedSources: r2.resolvedIds, syntheticNamedExports: r2.syntheticNamedExports }]))
      s2.updateOptions(n2), s2.setSource(await au(n2, s2, this.pluginDriver, this.options.onLog));
    else {
      if (r2.transformFiles)
        for (const e5 of r2.transformFiles)
          this.pluginDriver.emitFile(e5);
      s2.setSource(r2);
    }
  }
  async awaitLoadModulesPromise() {
    let e4;
    do {
      e4 = this.latestLoadModulesPromise, await e4;
    } while (e4 !== this.latestLoadModulesPromise);
  }
  extendLoadModulesPromise(e4) {
    return this.latestLoadModulesPromise = Promise.all([e4, this.latestLoadModulesPromise]), this.latestLoadModulesPromise.catch(() => {
    }), e4;
  }
  async fetchDynamicDependencies(e4, t2) {
    const s2 = await Promise.all(t2.map((t3) => t3.then(async ([t4, s3]) => null === s3 ? null : "string" == typeof s3 ? (t4.resolution = s3, null) : t4.resolution = await this.fetchResolvedDependency(V(s3.id), e4.id, s3))));
    for (const t3 of s2)
      t3 && (e4.dynamicDependencies.add(t3), t3.dynamicImporters.push(e4.id));
  }
  async fetchModule({ assertions: e4, id: t2, meta: s2, moduleSideEffects: i2, syntheticNamedExports: n2 }, r2, o2, a2) {
    const l2 = this.modulesById.get(t2);
    if (l2 instanceof jo)
      return r2 && wo(e4, l2.info.assertions) && this.options.onLog(Ae, Bt(l2.info.assertions, e4, t2, r2)), await this.handleExistingModule(l2, o2, a2), l2;
    if (l2 instanceof Jt)
      return Qe({ code: "EXTERNAL_MODULES_CANNOT_BE_TRANSFORMED_TO_MODULES", message: `${l2.id} is resolved as a module now, but it was an external module before. Please check whether there are conflicts in your Rollup options "external" and "manualChunks", manualChunks cannot include external modules.` });
    const c2 = new jo(this.graph, t2, this.options, o2, i2, n2, s2, e4);
    this.modulesById.set(t2, c2), this.graph.watchFiles[t2] = true;
    const h2 = this.addModuleSource(t2, r2, c2).then(() => [this.getResolveStaticDependencyPromises(c2), this.getResolveDynamicImportPromises(c2), u2]), u2 = pu(h2).then(() => this.pluginDriver.hookParallel("moduleParsed", [c2.info]));
    u2.catch(() => {
    }), this.moduleLoadPromises.set(c2, h2);
    const d2 = await h2;
    return a2 ? a2 === lu && await u2 : await this.fetchModuleDependencies(c2, ...d2), c2;
  }
  async fetchModuleDependencies(e4, t2, s2, i2) {
    this.modulesWithLoadedDependencies.has(e4) || (this.modulesWithLoadedDependencies.add(e4), await Promise.all([this.fetchStaticDependencies(e4, t2), this.fetchDynamicDependencies(e4, s2)]), e4.linkImports(), await i2);
  }
  fetchResolvedDependency(e4, t2, s2) {
    if (s2.external) {
      const { assertions: i2, external: n2, id: r2, moduleSideEffects: o2, meta: a2 } = s2;
      let l2 = this.modulesById.get(r2);
      if (l2) {
        if (!(l2 instanceof Jt))
          return Qe(function(e5, t3) {
            return { code: "INVALID_EXTERNAL_ID", message: `"${e5}" is imported as an external by "${V(t3)}", but is already an existing non-external module id.` };
          }(e4, t2));
        wo(l2.info.assertions, i2) && this.options.onLog(Ae, Bt(l2.info.assertions, i2, e4, t2));
      } else
        l2 = new Jt(this.options, r2, o2, a2, "absolute" !== n2 && I(r2), i2), this.modulesById.set(r2, l2);
      return Promise.resolve(l2);
    }
    return this.fetchModule(s2, t2, false, false);
  }
  async fetchStaticDependencies(e4, t2) {
    for (const s2 of await Promise.all(t2.map((t3) => t3.then(([t4, s3]) => this.fetchResolvedDependency(t4, e4.id, s3)))))
      e4.dependencies.add(s2), s2.importers.push(e4.id);
    if (!this.options.treeshake || "no-treeshake" === e4.info.moduleSideEffects)
      for (const t3 of e4.dependencies)
        t3 instanceof jo && (t3.importedFromNotTreeshaken = true);
  }
  getNormalizedResolvedIdWithoutDefaults(e4, t2, s2) {
    const { makeAbsoluteExternalsRelative: i2 } = this.options;
    if (e4) {
      if ("object" == typeof e4) {
        const n4 = e4.external || this.options.external(e4.id, t2, true);
        return { ...e4, external: n4 && ("relative" === n4 || !I(e4.id) || true === n4 && du(e4.id, s2, i2) || "absolute") };
      }
      const n3 = this.options.external(e4, t2, true);
      return { external: n3 && (du(e4, s2, i2) || "absolute"), id: n3 && i2 ? hu(e4, t2) : e4 };
    }
    const n2 = i2 ? hu(s2, t2) : s2;
    return false === e4 || this.options.external(n2, t2, true) ? { external: du(n2, s2, i2) || "absolute", id: n2 } : null;
  }
  getResolveDynamicImportPromises(e4) {
    return e4.dynamicImports.map(async (t2) => {
      const s2 = await this.resolveDynamicImport(e4, "string" == typeof t2.argument ? t2.argument : t2.argument.esTreeNode, e4.id, function(e5) {
        var _a2, _b, _c2;
        const t3 = (_c2 = (_b = (_a2 = e5.arguments) == null ? void 0 : _a2[0]) == null ? void 0 : _b.properties.find((e6) => "assert" === Io(e6))) == null ? void 0 : _c2.value;
        if (!t3)
          return ge;
        const s3 = t3.properties.map((e6) => {
          const t4 = Io(e6);
          return "string" == typeof t4 && "string" == typeof e6.value.value ? [t4, e6.value.value] : null;
        }).filter((e6) => !!e6);
        return s3.length > 0 ? Object.fromEntries(s3) : ge;
      }(t2.node));
      return s2 && "object" == typeof s2 && (t2.id = s2.id), [t2, s2];
    });
  }
  getResolveStaticDependencyPromises(e4) {
    return Array.from(e4.sourcesWithAssertions, async ([t2, s2]) => [t2, e4.resolvedIds[t2] = e4.resolvedIds[t2] || this.handleInvalidResolvedId(await this.resolveId(t2, e4.id, ge, false, s2), t2, e4.id, s2)]);
  }
  getResolvedIdWithDefaults(e4, t2) {
    if (!e4)
      return null;
    const s2 = e4.external || false;
    return { assertions: e4.assertions || t2, external: s2, id: e4.id, meta: e4.meta || {}, moduleSideEffects: e4.moduleSideEffects ?? this.hasModuleSideEffects(e4.id, !!s2), resolvedBy: e4.resolvedBy ?? "rollup", syntheticNamedExports: e4.syntheticNamedExports ?? false };
  }
  async handleExistingModule(e4, t2, s2) {
    const i2 = this.moduleLoadPromises.get(e4);
    if (s2)
      return s2 === lu ? pu(i2) : i2;
    if (t2) {
      e4.info.isEntry = true, this.implicitEntryModules.delete(e4);
      for (const t3 of e4.implicitlyLoadedAfter)
        t3.implicitlyLoadedBefore.delete(e4);
      e4.implicitlyLoadedAfter.clear();
    }
    return this.fetchModuleDependencies(e4, ...await i2);
  }
  handleInvalidResolvedId(e4, t2, s2, i2) {
    return null === e4 ? w(t2) ? Qe(function(e5, t3) {
      return { code: _t, exporter: e5, id: t3, message: `Could not resolve "${e5}" from "${V(t3)}"` };
    }(t2, s2)) : (this.options.onLog(Ae, function(e5, t3) {
      return { code: _t, exporter: e5, id: t3, message: `"${e5}" is imported by "${V(t3)}", but could not be resolved – treating it as an external dependency.`, url: De("troubleshooting/#warning-treating-module-as-external-dependency") };
    }(t2, s2)), { assertions: i2, external: true, id: t2, meta: {}, moduleSideEffects: this.hasModuleSideEffects(t2, true), resolvedBy: "rollup", syntheticNamedExports: false }) : (e4.external && e4.syntheticNamedExports && this.options.onLog(Ae, function(e5, t3) {
      return { code: "EXTERNAL_SYNTHETIC_EXPORTS", exporter: e5, message: `External "${e5}" cannot have "syntheticNamedExports" enabled (imported by "${V(t3)}").` };
    }(t2, s2)), e4);
  }
  async loadEntryModule(e4, t2, s2, i2, n2 = false) {
    const r2 = await Wh(e4, s2, this.options.preserveSymlinks, this.pluginDriver, this.resolveId, null, ge, true, ge);
    if (null == r2)
      return Qe(null === i2 ? function(e5) {
        return { code: Nt, message: `Could not resolve entry module "${V(e5)}".` };
      }(e4) : function(e5, t3) {
        return { code: Et, message: `Module "${V(e5)}" that should be implicitly loaded before "${V(t3)}" could not be resolved.` };
      }(e4, i2));
    const o2 = "object" == typeof r2 && r2.external;
    return false === r2 || o2 ? Qe(null === i2 ? o2 && n2 ? { code: "EXTERNAL_MODULES_CANNOT_BE_INCLUDED_IN_MANUAL_CHUNKS", message: `"${e4}" cannot be included in manualChunks because it is resolved as an external module by the "external" option or plugins.` } : function(e5) {
      return { code: Nt, message: `Entry module "${V(e5)}" cannot be external.` };
    }(e4) : function(e5, t3) {
      return { code: Et, message: `Module "${V(e5)}" that should be implicitly loaded before "${V(t3)}" cannot be external.` };
    }(e4, i2)) : this.fetchModule(this.getResolvedIdWithDefaults("object" == typeof r2 ? r2 : { id: r2 }, ge), void 0, t2, false);
  }
  async resolveDynamicImport(e4, t2, s2, i2) {
    const n2 = await this.pluginDriver.hookFirst("resolveDynamicImport", [t2, s2, { assertions: i2 }]);
    if ("string" != typeof t2)
      return "string" == typeof n2 ? n2 : n2 ? this.getResolvedIdWithDefaults(n2, i2) : null;
    if (null == n2) {
      const n3 = e4.resolvedIds[t2];
      return n3 ? (wo(n3.assertions, i2) && this.options.onLog(Ae, Bt(n3.assertions, i2, t2, s2)), n3) : e4.resolvedIds[t2] = this.handleInvalidResolvedId(await this.resolveId(t2, e4.id, ge, false, i2), t2, e4.id, i2);
    }
    return this.handleInvalidResolvedId(this.getResolvedIdWithDefaults(this.getNormalizedResolvedIdWithoutDefaults(n2, s2, t2), i2), t2, s2, i2);
  }
}
function hu(e4, t2) {
  return w(e4) ? t2 ? R(t2, "..", e4) : R(e4) : e4;
}
function uu(e4, { fileName: t2, name: s2 }, i2, n2) {
  var _a2;
  if (null !== t2)
    e4.chunkFileNames.add(t2);
  else if (null !== s2) {
    let t3 = 0;
    for (; ((_a2 = e4.chunkNames[t3]) == null ? void 0 : _a2.priority) < n2; )
      t3++;
    e4.chunkNames.splice(t3, 0, { isUserDefined: i2, name: s2, priority: n2 });
  }
}
function du(e4, t2, s2) {
  return true === s2 || "ifRelativeSource" === s2 && w(t2) || !I(e4);
}
async function pu(e4) {
  const [t2, s2] = await e4;
  return Promise.all([...t2, ...s2]);
}
class fu extends Ti {
  constructor() {
    super(), this.parent = null, this.variables.set("undefined", new co());
  }
  findVariable(e4) {
    let t2 = this.variables.get(e4);
    return t2 || (t2 = new ln(e4), this.variables.set(e4, t2)), t2;
  }
}
function mu(e4) {
  return Gl().update(e4).digest("hex");
}
function gu(e4, t2, s2, i2, n2) {
  const r2 = i2.sanitizeFileName(e4 || "asset");
  return Ma(Ra("function" == typeof i2.assetFileNames ? i2.assetFileNames({ name: e4, source: t2, type: "asset" }) : i2.assetFileNames, "output.assetFileNames", { ext: () => N(r2).slice(1), extname: () => N(r2), hash: (e5) => s2.slice(0, Math.max(0, e5 || 8)), name: () => r2.slice(0, Math.max(0, r2.length - N(r2).length)) }), n2);
}
function yu(e4, { bundle: t2 }, s2) {
  t2[Na].has(e4.toLowerCase()) ? s2(Ae, function(e5) {
    return { code: lt, message: `The emitted file "${e5}" overwrites a previously emitted file of the same name.` };
  }(e4)) : t2[e4] = _a;
}
const xu = /* @__PURE__ */ new Set(["chunk", "asset", "prebuilt-chunk"]);
function Eu(e4, t2, s2) {
  if (!("string" == typeof e4 || e4 instanceof Uint8Array)) {
    const e5 = t2.fileName || t2.name || s2;
    return Qe(Xt(`Could not set source for ${"string" == typeof e5 ? `asset "${e5}"` : "unnamed asset"}, asset source needs to be a string, Uint8Array or Buffer.`));
  }
  return e4;
}
function bu(e4, t2) {
  return "string" != typeof e4.fileName ? Qe((s2 = e4.name || t2, { code: st, message: `Plugin error - Unable to get file name for asset "${s2}". Ensure that the source is set and that generate is called first. If you reference assets via import.meta.ROLLUP_FILE_URL_<referenceId>, you need to either have set their source after "renderStart" or need to provide an explicit "fileName" when emitting them.` })) : e4.fileName;
  var s2;
}
function vu(e4, t2) {
  return e4.fileName ? e4.fileName : t2 ? t2.get(e4.module).getFileName() : Qe((s2 = e4.fileName || e4.name, { code: nt, message: `Plugin error - Unable to get file name for emitted chunk "${s2}". You can only get file names once chunks have been generated after the "renderStart" hook.` }));
  var s2;
}
class Su {
  constructor(e4, t2, s2) {
    this.graph = e4, this.options = t2, this.facadeChunkByModule = null, this.nextIdBase = 1, this.output = null, this.outputFileEmitters = [], this.emitFile = (e5) => function(e6) {
      return Boolean(e6 && xu.has(e6.type));
    }(e5) ? "prebuilt-chunk" === e5.type ? this.emitPrebuiltChunk(e5) : function(e6) {
      const t3 = e6.fileName || e6.name;
      return !t3 || "string" == typeof t3 && !B(t3);
    }(e5) ? "chunk" === e5.type ? this.emitChunk(e5) : this.emitAsset(e5) : Qe(Xt(`The "fileName" or "name" properties of emitted chunks and assets must be strings that are neither absolute nor relative paths, received "${e5.fileName || e5.name}".`)) : Qe(Xt(`Emitted files must be of type "asset", "chunk" or "prebuilt-chunk", received "${e5 && e5.type}".`)), this.finaliseAssets = () => {
      for (const [e5, t3] of this.filesByReferenceId)
        if ("asset" === t3.type && "string" != typeof t3.fileName)
          return Qe({ code: "ASSET_SOURCE_MISSING", message: `Plugin error creating asset "${t3.name || e5}" - no asset source set.` });
    }, this.getFileName = (e5) => {
      const t3 = this.filesByReferenceId.get(e5);
      return t3 ? "chunk" === t3.type ? vu(t3, this.facadeChunkByModule) : "prebuilt-chunk" === t3.type ? t3.fileName : bu(t3, e5) : Qe({ code: "FILE_NOT_FOUND", message: `Plugin error - Unable to get file name for unknown file "${e5}".` });
    }, this.setAssetSource = (e5, t3) => {
      const s3 = this.filesByReferenceId.get(e5);
      if (!s3)
        return Qe({ code: "ASSET_NOT_FOUND", message: `Plugin error - Unable to set the source for unknown asset "${e5}".` });
      if ("asset" !== s3.type)
        return Qe(Xt(`Asset sources can only be set for emitted assets but "${e5}" is an emitted chunk.`));
      if (void 0 !== s3.source)
        return Qe({ code: "ASSET_SOURCE_ALREADY_SET", message: `Unable to set the source for asset "${s3.name || e5}", source already set.` });
      const i2 = Eu(t3, s3, e5);
      if (this.output)
        this.finalizeAdditionalAsset(s3, i2, this.output);
      else {
        s3.source = i2;
        for (const e6 of this.outputFileEmitters)
          e6.finalizeAdditionalAsset(s3, i2, e6.output);
      }
    }, this.setChunkInformation = (e5) => {
      this.facadeChunkByModule = e5;
    }, this.setOutputBundle = (e5, t3) => {
      const s3 = this.output = { bundle: e5, fileNamesBySource: /* @__PURE__ */ new Map(), outputOptions: t3 };
      for (const e6 of this.filesByReferenceId.values())
        e6.fileName && yu(e6.fileName, s3, this.options.onLog);
      const i2 = /* @__PURE__ */ new Map();
      for (const e6 of this.filesByReferenceId.values())
        if ("asset" === e6.type && void 0 !== e6.source)
          if (e6.fileName)
            this.finalizeAdditionalAsset(e6, e6.source, s3);
          else {
            U(i2, mu(e6.source), () => []).push(e6);
          }
        else
          "prebuilt-chunk" === e6.type && (this.output.bundle[e6.fileName] = this.createPrebuiltChunk(e6));
      for (const [e6, t4] of i2)
        this.finalizeAssetsWithSameSource(t4, e6, s3);
    }, this.filesByReferenceId = s2 ? new Map(s2.filesByReferenceId) : /* @__PURE__ */ new Map(), s2 == null ? void 0 : s2.addOutputFileEmitter(this);
  }
  addOutputFileEmitter(e4) {
    this.outputFileEmitters.push(e4);
  }
  assignReferenceId(e4, t2) {
    let s2 = t2;
    do {
      s2 = Gl().update(s2).digest("hex").slice(0, 8);
    } while (this.filesByReferenceId.has(s2) || this.outputFileEmitters.some(({ filesByReferenceId: e5 }) => e5.has(s2)));
    e4.referenceId = s2, this.filesByReferenceId.set(s2, e4);
    for (const { filesByReferenceId: t3 } of this.outputFileEmitters)
      t3.set(s2, e4);
    return s2;
  }
  createPrebuiltChunk(e4) {
    return { code: e4.code, dynamicImports: [], exports: e4.exports || [], facadeModuleId: null, fileName: e4.fileName, implicitlyLoadedBefore: [], importedBindings: {}, imports: [], isDynamicEntry: false, isEntry: false, isImplicitEntry: false, map: e4.map || null, moduleIds: [], modules: {}, name: e4.fileName, preliminaryFileName: e4.fileName, referencedFiles: [], sourcemapFileName: e4.sourcemapFileName || null, type: "chunk" };
  }
  emitAsset(e4) {
    const t2 = void 0 === e4.source ? void 0 : Eu(e4.source, e4, null), s2 = { fileName: e4.fileName, name: e4.name, needsCodeReference: !!e4.needsCodeReference, referenceId: "", source: t2, type: "asset" }, i2 = this.assignReferenceId(s2, e4.fileName || e4.name || String(this.nextIdBase++));
    if (this.output)
      this.emitAssetWithReferenceId(s2, this.output);
    else
      for (const e5 of this.outputFileEmitters)
        e5.emitAssetWithReferenceId(s2, e5.output);
    return i2;
  }
  emitAssetWithReferenceId(e4, t2) {
    const { fileName: s2, source: i2 } = e4;
    s2 && yu(s2, t2, this.options.onLog), void 0 !== i2 && this.finalizeAdditionalAsset(e4, i2, t2);
  }
  emitChunk(e4) {
    if (this.graph.phase > bo.LOAD_AND_PARSE)
      return Qe({ code: mt, message: "Cannot emit chunks after module loading has finished." });
    if ("string" != typeof e4.id)
      return Qe(Xt(`Emitted chunks need to have a valid string id, received "${e4.id}"`));
    const t2 = { fileName: e4.fileName, module: null, name: e4.name || e4.id, referenceId: "", type: "chunk" };
    return this.graph.moduleLoader.emitChunk(e4).then((e5) => t2.module = e5).catch(() => {
    }), this.assignReferenceId(t2, e4.id);
  }
  emitPrebuiltChunk(e4) {
    if ("string" != typeof e4.code)
      return Qe(Xt(`Emitted prebuilt chunks need to have a valid string code, received "${e4.code}".`));
    if ("string" != typeof e4.fileName || B(e4.fileName))
      return Qe(Xt(`The "fileName" property of emitted prebuilt chunks must be strings that are neither absolute nor relative paths, received "${e4.fileName}".`));
    const t2 = { code: e4.code, exports: e4.exports, fileName: e4.fileName, map: e4.map, referenceId: "", type: "prebuilt-chunk" }, s2 = this.assignReferenceId(t2, t2.fileName);
    return this.output && (this.output.bundle[t2.fileName] = this.createPrebuiltChunk(t2)), s2;
  }
  finalizeAdditionalAsset(e4, t2, { bundle: s2, fileNamesBySource: i2, outputOptions: n2 }) {
    let { fileName: r2, needsCodeReference: o2, referenceId: a2 } = e4;
    if (!r2) {
      const o3 = mu(t2);
      r2 = i2.get(o3), r2 || (r2 = gu(e4.name, t2, o3, n2, s2), i2.set(o3, r2));
    }
    const l2 = { ...e4, fileName: r2, source: t2 };
    this.filesByReferenceId.set(a2, l2);
    const c2 = s2[r2];
    "asset" === (c2 == null ? void 0 : c2.type) ? c2.needsCodeReference && (c2.needsCodeReference = o2) : s2[r2] = { fileName: r2, name: e4.name, needsCodeReference: o2, source: t2, type: "asset" };
  }
  finalizeAssetsWithSameSource(e4, t2, { bundle: s2, fileNamesBySource: i2, outputOptions: n2 }) {
    let r2, o2 = "", a2 = true;
    for (const i3 of e4) {
      a2 && (a2 = i3.needsCodeReference);
      const e5 = gu(i3.name, i3.source, t2, n2, s2);
      (!o2 || e5.length < o2.length || e5.length === o2.length && e5 < o2) && (o2 = e5, r2 = i3);
    }
    i2.set(t2, o2);
    for (const t3 of e4) {
      const e5 = { ...t3, fileName: o2 };
      this.filesByReferenceId.set(t3.referenceId, e5);
    }
    s2[o2] = { fileName: o2, name: r2.name, needsCodeReference: a2, source: r2.source, type: "asset" };
  }
}
function Au(e4, t2, s2, i2, n2) {
  return we[e4] < we[n2] ? Ui : (n3, r2) => {
    null != r2 && s2(Ae, { code: dt, message: `Plugin "${i2}" tried to add a file position to a log or warning. This is only supported in the "transform" hook at the moment and will be ignored.` }), (n3 = Jh(n3)).code && !n3.pluginCode && (n3.pluginCode = n3.code), n3.code = t2, n3.plugin = i2, s2(e4, n3);
  };
}
function ku(t2, s2, i2, n2, r2, o2) {
  const { logLevel: a2, onLog: l2 } = n2;
  let c2, h2 = true;
  if ("string" != typeof t2.cacheKey && (t2.name.startsWith(qh) || t2.name.startsWith(Hh) || o2.has(t2.name) ? h2 = false : o2.add(t2.name)), s2)
    if (h2) {
      const e4 = t2.cacheKey || t2.name;
      d2 = s2[e4] || (s2[e4] = /* @__PURE__ */ Object.create(null)), c2 = { delete: (e5) => delete d2[e5], get(e5) {
        const t3 = d2[e5];
        if (t3)
          return t3[0] = 0, t3[1];
      }, has(e5) {
        const t3 = d2[e5];
        return !!t3 && (t3[0] = 0, true);
      }, set(e5, t3) {
        d2[e5] = [0, t3];
      } };
    } else
      u2 = t2.name, c2 = { delete: () => Yh(u2), get: () => Yh(u2), has: () => Yh(u2), set: () => Yh(u2) };
  else
    c2 = Kh;
  var u2, d2;
  return { addWatchFile(e4) {
    if (i2.phase >= bo.GENERATE)
      return this.error({ code: mt, message: 'Cannot call "addWatchFile" after the build has finished.' });
    i2.watchFiles[e4] = true;
  }, cache: c2, debug: Au(Ie, "PLUGIN_LOG", l2, t2.name, a2), emitFile: r2.emitFile.bind(r2), error: (e4) => Qe(qt(Jh(e4), t2.name)), getFileName: r2.getFileName, getModuleIds: () => i2.modulesById.keys(), getModuleInfo: i2.getModuleInfo, getWatchFiles: () => Object.keys(i2.watchFiles), info: Au(ke, "PLUGIN_LOG", l2, t2.name, a2), load: (e4) => i2.moduleLoader.preloadModule(e4), meta: { rollupVersion: e, watchMode: i2.watchMode }, get moduleIds() {
    const e4 = i2.modulesById.keys();
    return function* () {
      Qt(`Accessing "this.moduleIds" on the plugin context by plugin ${t2.name} is deprecated. The "this.getModuleIds" plugin context function should be used instead.`, "plugin-development/#this-getmoduleids", true, n2, t2.name), yield* e4;
    }();
  }, parse: i2.contextParse.bind(i2), resolve: (e4, s3, { assertions: n3, custom: r3, isEntry: o3, skipSelf: a3 } = me) => i2.moduleLoader.resolveId(e4, s3, r3, o3, n3 || ge, a3 ? [{ importer: s3, plugin: t2, source: e4 }] : null), setAssetSource: r2.setAssetSource, warn: Au(Ae, "PLUGIN_WARNING", l2, t2.name, a2) };
}
const Iu = Object.keys({ buildEnd: 1, buildStart: 1, closeBundle: 1, closeWatcher: 1, load: 1, moduleParsed: 1, onLog: 1, options: 1, resolveDynamicImport: 1, resolveId: 1, shouldTransformCachedModule: 1, transform: 1, watchChange: 1 });
class wu {
  constructor(e4, t2, s2, i2, n2) {
    this.graph = e4, this.options = t2, this.pluginCache = i2, this.sortedPlugins = /* @__PURE__ */ new Map(), this.unfulfilledActions = /* @__PURE__ */ new Set(), this.fileEmitter = new Su(e4, t2, n2 && n2.fileEmitter), this.emitFile = this.fileEmitter.emitFile.bind(this.fileEmitter), this.getFileName = this.fileEmitter.getFileName.bind(this.fileEmitter), this.finaliseAssets = this.fileEmitter.finaliseAssets.bind(this.fileEmitter), this.setChunkInformation = this.fileEmitter.setChunkInformation.bind(this.fileEmitter), this.setOutputBundle = this.fileEmitter.setOutputBundle.bind(this.fileEmitter), this.plugins = [...n2 ? n2.plugins : [], ...s2];
    const r2 = /* @__PURE__ */ new Set();
    if (this.pluginContexts = new Map(this.plugins.map((s3) => [s3, ku(s3, i2, e4, t2, this.fileEmitter, r2)])), n2)
      for (const e5 of s2)
        for (const s3 of Iu)
          s3 in e5 && t2.onLog(Ae, (o2 = e5.name, { code: "INPUT_HOOK_IN_OUTPUT_PLUGIN", message: `The "${s3}" hook used by the output plugin ${o2} is a build time hook and will not be run for that plugin. Either this plugin cannot be used as an output plugin, or it should have an option to configure it as an output plugin.` }));
    var o2;
  }
  createOutputPluginDriver(e4) {
    return new wu(this.graph, this.options, e4, this.pluginCache, this);
  }
  getUnfulfilledHookActions() {
    return this.unfulfilledActions;
  }
  hookFirst(e4, t2, s2, i2) {
    return this.hookFirstAndGetPlugin(e4, t2, s2, i2).then((e5) => e5 && e5[0]);
  }
  async hookFirstAndGetPlugin(e4, t2, s2, i2) {
    for (const n2 of this.getSortedPlugins(e4)) {
      if (i2 == null ? void 0 : i2.has(n2))
        continue;
      const r2 = await this.runHook(e4, t2, n2, s2);
      if (null != r2)
        return [r2, n2];
    }
    return null;
  }
  hookFirstSync(e4, t2, s2) {
    for (const i2 of this.getSortedPlugins(e4)) {
      const n2 = this.runHookSync(e4, t2, i2, s2);
      if (null != n2)
        return n2;
    }
    return null;
  }
  async hookParallel(e4, t2, s2) {
    const i2 = [];
    for (const n2 of this.getSortedPlugins(e4))
      n2[e4].sequential ? (await Promise.all(i2), i2.length = 0, await this.runHook(e4, t2, n2, s2)) : i2.push(this.runHook(e4, t2, n2, s2));
    await Promise.all(i2);
  }
  hookReduceArg0(e4, [t2, ...s2], i2, n2) {
    let r2 = Promise.resolve(t2);
    for (const t3 of this.getSortedPlugins(e4))
      r2 = r2.then((r3) => this.runHook(e4, [r3, ...s2], t3, n2).then((e5) => i2.call(this.pluginContexts.get(t3), r3, e5, t3)));
    return r2;
  }
  hookReduceArg0Sync(e4, [t2, ...s2], i2, n2) {
    for (const r2 of this.getSortedPlugins(e4)) {
      const o2 = [t2, ...s2], a2 = this.runHookSync(e4, o2, r2, n2);
      t2 = i2.call(this.pluginContexts.get(r2), t2, a2, r2);
    }
    return t2;
  }
  async hookReduceValue(e4, t2, s2, i2) {
    const n2 = [], r2 = [];
    for (const t3 of this.getSortedPlugins(e4, $u))
      t3[e4].sequential ? (n2.push(...await Promise.all(r2)), r2.length = 0, n2.push(await this.runHook(e4, s2, t3))) : r2.push(this.runHook(e4, s2, t3));
    return n2.push(...await Promise.all(r2)), n2.reduce(i2, await t2);
  }
  hookReduceValueSync(e4, t2, s2, i2, n2) {
    let r2 = t2;
    for (const t3 of this.getSortedPlugins(e4)) {
      const o2 = this.runHookSync(e4, s2, t3, n2);
      r2 = i2.call(this.pluginContexts.get(t3), r2, o2, t3);
    }
    return r2;
  }
  hookSeq(e4, t2, s2) {
    let i2 = Promise.resolve();
    for (const n2 of this.getSortedPlugins(e4))
      i2 = i2.then(() => this.runHook(e4, t2, n2, s2));
    return i2.then(Nu);
  }
  getSortedPlugins(e4, t2) {
    return U(this.sortedPlugins, e4, () => Pu(e4, this.plugins, t2));
  }
  runHook(e4, t2, s2, i2) {
    const n2 = s2[e4], r2 = "object" == typeof n2 ? n2.handler : n2;
    let o2 = this.pluginContexts.get(s2);
    i2 && (o2 = i2(o2, s2));
    let a2 = null;
    return Promise.resolve().then(() => {
      if ("function" != typeof r2)
        return r2;
      const i3 = r2.apply(o2, t2);
      return (i3 == null ? void 0 : i3.then) ? (a2 = [s2.name, e4, t2], this.unfulfilledActions.add(a2), Promise.resolve(i3).then((e5) => (this.unfulfilledActions.delete(a2), e5))) : i3;
    }).catch((t3) => (null !== a2 && this.unfulfilledActions.delete(a2), Qe(qt(t3, s2.name, { hook: e4 }))));
  }
  runHookSync(e4, t2, s2, i2) {
    const n2 = s2[e4], r2 = "object" == typeof n2 ? n2.handler : n2;
    let o2 = this.pluginContexts.get(s2);
    i2 && (o2 = i2(o2, s2));
    try {
      return r2.apply(o2, t2);
    } catch (t3) {
      return Qe(qt(t3, s2.name, { hook: e4 }));
    }
  }
}
function Pu(e4, t2, s2 = Cu) {
  const i2 = [], n2 = [], r2 = [];
  for (const o2 of t2) {
    const t3 = o2[e4];
    if (t3) {
      if ("object" == typeof t3) {
        if (s2(t3.handler, e4, o2), "pre" === t3.order) {
          i2.push(o2);
          continue;
        }
        if ("post" === t3.order) {
          r2.push(o2);
          continue;
        }
      } else
        s2(t3, e4, o2);
      n2.push(o2);
    }
  }
  return [...i2, ...n2, ...r2];
}
function Cu(e4, t2, s2) {
  "function" != typeof e4 && Qe(function(e5, t3) {
    return { code: ft, hook: e5, message: `Error running plugin hook "${e5}" for plugin "${t3}", expected a function hook or an object with a "handler" function.`, plugin: t3 };
  }(t2, s2.name));
}
function $u(e4, t2, s2) {
  if ("string" != typeof e4 && "function" != typeof e4)
    return Qe(function(e5, t3) {
      return { code: ft, hook: e5, message: `Error running plugin hook "${e5}" for plugin "${t3}", expected a string, a function hook or an object with a "handler" string or function.`, plugin: t3 };
    }(t2, s2.name));
}
function Nu() {
}
class _u {
  constructor(e4) {
    this.maxParallel = e4, this.queue = [], this.workerCount = 0;
  }
  run(e4) {
    return new Promise((t2, s2) => {
      this.queue.push({ reject: s2, resolve: t2, task: e4 }), this.work();
    });
  }
  async work() {
    if (this.workerCount >= this.maxParallel)
      return;
    let e4;
    for (this.workerCount++; e4 = this.queue.shift(); ) {
      const { reject: t2, resolve: s2, task: i2 } = e4;
      try {
        s2(await i2());
      } catch (e5) {
        t2(e5);
      }
    }
    this.workerCount--;
  }
}
class Ru {
  constructor(e4, t2) {
    var _a2, _b;
    if (this.options = e4, this.astLru = function(e5) {
      var t3, s2, i2, n2 = e5 || 1;
      function r2(e6, r3) {
        ++t3 > n2 && (i2 = s2, o2(1), ++t3), s2[e6] = r3;
      }
      function o2(e6) {
        t3 = 0, s2 = /* @__PURE__ */ Object.create(null), e6 || (i2 = /* @__PURE__ */ Object.create(null));
      }
      return o2(), { clear: o2, has: function(e6) {
        return void 0 !== s2[e6] || void 0 !== i2[e6];
      }, get: function(e6) {
        var t4 = s2[e6];
        return void 0 !== t4 ? t4 : void 0 !== (t4 = i2[e6]) ? (r2(e6, t4), t4) : void 0;
      }, set: function(e6, t4) {
        void 0 !== s2[e6] ? s2[e6] = t4 : r2(e6, t4);
      } };
    }(5), this.cachedModules = /* @__PURE__ */ new Map(), this.deoptimizationTracker = new te(), this.entryModules = [], this.modulesById = /* @__PURE__ */ new Map(), this.needsTreeshakingPass = false, this.phase = bo.LOAD_AND_PARSE, this.scope = new fu(), this.watchFiles = /* @__PURE__ */ Object.create(null), this.watchMode = false, this.externalModules = [], this.implicitEntryModules = [], this.modules = [], this.getModuleInfo = (e5) => {
      const t3 = this.modulesById.get(e5);
      return t3 ? t3.info : null;
    }, false !== e4.cache) {
      if ((_a2 = e4.cache) == null ? void 0 : _a2.modules)
        for (const t3 of e4.cache.modules)
          this.cachedModules.set(t3.id, t3);
      this.pluginCache = ((_b = e4.cache) == null ? void 0 : _b.plugins) || /* @__PURE__ */ Object.create(null);
      for (const e5 in this.pluginCache) {
        const t3 = this.pluginCache[e5];
        for (const e6 of Object.values(t3))
          e6[0]++;
      }
    }
    if (t2) {
      this.watchMode = true;
      const e5 = (...e6) => this.pluginDriver.hookParallel("watchChange", e6), s2 = () => this.pluginDriver.hookParallel("closeWatcher", []);
      t2.onCurrentRun("change", e5), t2.onCurrentRun("close", s2);
    }
    this.pluginDriver = new wu(this, e4, e4.plugins, this.pluginCache), this.acornParser = Tc.extend(...e4.acornInjectPlugins), this.moduleLoader = new cu(this, this.modulesById, this.options, this.pluginDriver), this.fileOperationQueue = new _u(e4.maxParallelFileOps), this.pureFunctions = (({ treeshake: e5 }) => {
      const t3 = /* @__PURE__ */ Object.create(null);
      for (const s2 of e5 ? e5.manualPureFunctions : []) {
        let e6 = t3;
        for (const t4 of s2.split("."))
          e6 = e6[t4] || (e6[t4] = /* @__PURE__ */ Object.create(null));
        e6[ji] = true;
      }
      return t3;
    })(e4);
  }
  async build() {
    Oo("generate module graph", 2), await this.generateModuleGraph(), Do("generate module graph", 2), Oo("sort and bind modules", 2), this.phase = bo.ANALYSE, this.sortModules(), Do("sort and bind modules", 2), Oo("mark included statements", 2), this.includeStatements(), Do("mark included statements", 2), this.phase = bo.GENERATE;
  }
  contextParse(e4, t2 = {}) {
    const s2 = t2.onComment, i2 = [];
    t2.onComment = s2 && "function" == typeof s2 ? (e5, n3, r2, o2, ...a2) => (i2.push({ end: o2, start: r2, type: e5 ? "Block" : "Line", value: n3 }), s2.call(t2, e5, n3, r2, o2, ...a2)) : i2;
    const n2 = this.acornParser.parse(e4, { ...this.options.acorn, ...t2 });
    return "object" == typeof s2 && s2.push(...i2), t2.onComment = s2, function(e5, t3, s3) {
      const i3 = [], n3 = [];
      for (const t4 of e5) {
        for (const [e6, s4] of Xs)
          s4.test(t4.value) && i3.push({ ...t4, annotationType: e6 });
        js.test(t4.value) && n3.push(t4);
      }
      for (const e6 of n3)
        Qs(t3, e6, false);
      Ws(t3, { annotationIndex: 0, annotations: i3, code: s3 });
    }(i2, n2, e4), n2;
  }
  getCache() {
    for (const e4 in this.pluginCache) {
      const t2 = this.pluginCache[e4];
      let s2 = true;
      for (const [e5, i2] of Object.entries(t2))
        i2[0] >= this.options.experimentalCacheExpiry ? delete t2[e5] : s2 = false;
      s2 && delete this.pluginCache[e4];
    }
    return { modules: this.modules.map((e4) => e4.toJSON()), plugins: this.pluginCache };
  }
  async generateModuleGraph() {
    var e4;
    if ({ entryModules: this.entryModules, implicitEntryModules: this.implicitEntryModules } = await this.moduleLoader.addEntryModules((e4 = this.options.input, Array.isArray(e4) ? e4.map((e5) => ({ fileName: null, id: e5, implicitlyLoadedAfter: [], importer: void 0, name: null })) : Object.entries(e4).map(([e5, t2]) => ({ fileName: null, id: t2, implicitlyLoadedAfter: [], importer: void 0, name: e5 }))), true), 0 === this.entryModules.length)
      throw new Error("You must supply options.input to rollup");
    for (const e5 of this.modulesById.values())
      e5 instanceof jo ? this.modules.push(e5) : this.externalModules.push(e5);
  }
  includeStatements() {
    const e4 = [...this.entryModules, ...this.implicitEntryModules];
    for (const t2 of e4)
      Vo(t2);
    if (this.options.treeshake) {
      let t2 = 1;
      do {
        Oo(`treeshaking pass ${t2}`, 3), this.needsTreeshakingPass = false;
        for (const e5 of this.modules)
          e5.isExecuted && ("no-treeshake" === e5.info.moduleSideEffects ? e5.includeAllInBundle() : e5.include());
        if (1 === t2)
          for (const t3 of e4)
            false !== t3.preserveSignature && (t3.includeAllExports(false), this.needsTreeshakingPass = true);
        Do("treeshaking pass " + t2++, 3);
      } while (this.needsTreeshakingPass);
    } else
      for (const e5 of this.modules)
        e5.includeAllInBundle();
    for (const e5 of this.externalModules)
      e5.warnUnusedImports();
    for (const e5 of this.implicitEntryModules)
      for (const t2 of e5.implicitlyLoadedAfter)
        t2.info.isEntry || t2.isIncluded() || Qe(Gt(t2));
  }
  sortModules() {
    const { orderedModules: e4, cyclePaths: t2 } = function(e5) {
      let t3 = 0;
      const s2 = [], i2 = /* @__PURE__ */ new Set(), n2 = /* @__PURE__ */ new Set(), r2 = /* @__PURE__ */ new Map(), o2 = [], a2 = (e6) => {
        if (e6 instanceof jo) {
          for (const t4 of e6.dependencies)
            r2.has(t4) ? i2.has(t4) || s2.push(Ja(t4, e6, r2)) : (r2.set(t4, e6), a2(t4));
          for (const t4 of e6.implicitlyLoadedBefore)
            n2.add(t4);
          for (const { resolution: t4 } of e6.dynamicImports)
            t4 instanceof jo && n2.add(t4);
          o2.push(e6);
        }
        e6.execIndex = t3++, i2.add(e6);
      };
      for (const t4 of e5)
        r2.has(t4) || (r2.set(t4, null), a2(t4));
      for (const e6 of n2)
        r2.has(e6) || (r2.set(e6, null), a2(e6));
      return { cyclePaths: s2, orderedModules: o2 };
    }(this.entryModules);
    for (const e5 of t2)
      this.options.onLog(Ae, Lt(e5));
    this.modules = e4;
    for (const e5 of this.modules)
      e5.bindReferences();
    this.warnForMissingExports();
  }
  warnForMissingExports() {
    for (const e4 of this.modules)
      for (const t2 of e4.importDescriptions.values())
        "*" === t2.name || t2.module.getVariableForExportName(t2.name)[0] || e4.log(Ae, Ut(t2.name, e4.id, t2.module.id), t2.start);
  }
}
function Mu(e4, t2) {
  return t2();
}
function Ou(t2, s2, i2, n2) {
  t2 = Pu("onLog", t2);
  const r2 = we[n2], o2 = (n3, a2, l2 = xe) => {
    if (!(we[n3] < r2)) {
      for (const s3 of t2) {
        if (l2.has(s3))
          continue;
        const { onLog: t3 } = s3, c2 = (e4) => we[e4] < r2 ? Ui : (t4) => o2(e4, Jh(t4), new Set(l2).add(s3));
        if (false === ("handler" in t3 ? t3.handler : t3).call({ debug: c2(Ie), error: (e4) => Qe(Jh(e4)), info: c2(ke), meta: { rollupVersion: e, watchMode: i2 }, warn: c2(Ae) }, n3, a2))
          return;
      }
      s2(n3, a2);
    }
  };
  return o2;
}
const Du = "{".charCodeAt(0), Lu = " ".charCodeAt(0), Tu = "assert";
function Vu(e4) {
  const t2 = e4.acorn || zh, { tokTypes: s2, TokenType: i2 } = t2;
  return class extends e4 {
    constructor(...e5) {
      super(...e5), this.assertToken = new i2(Tu);
    }
    _codeAt(e5) {
      return this.input.charCodeAt(e5);
    }
    _eat(e5) {
      this.type !== e5 && this.unexpected(), this.next();
    }
    readToken(e5) {
      let t3 = 0;
      for (; t3 < 6; t3++)
        if (this._codeAt(this.pos + t3) !== Tu.charCodeAt(t3))
          return super.readToken(e5);
      for (; this._codeAt(this.pos + t3) !== Du; t3++)
        if (this._codeAt(this.pos + t3) !== Lu)
          return super.readToken(e5);
      return "{" === this.type.label ? super.readToken(e5) : (this.pos += 6, this.finishToken(this.assertToken));
    }
    parseDynamicImport(e5) {
      if (this.next(), e5.source = this.parseMaybeAssign(), this.eat(s2.comma)) {
        const t3 = this.parseObj(false);
        e5.arguments = [t3];
      }
      return this._eat(s2.parenR), this.finishNode(e5, "ImportExpression");
    }
    parseExport(e5, t3) {
      if (this.next(), this.eat(s2.star)) {
        if (this.options.ecmaVersion >= 11 && (this.eatContextual("as") ? (e5.exported = this.parseIdent(true), this.checkExport(t3, e5.exported.name, this.lastTokStart)) : e5.exported = null), this.expectContextual("from"), this.type !== s2.string && this.unexpected(), e5.source = this.parseExprAtom(), this.type === this.assertToken || this.type === s2._with) {
          this.next();
          const t4 = this.parseImportAssertions();
          t4 && (e5.assertions = t4);
        }
        return this.semicolon(), this.finishNode(e5, "ExportAllDeclaration");
      }
      if (this.eat(s2._default)) {
        var i3;
        if (this.checkExport(t3, "default", this.lastTokStart), this.type === s2._function || (i3 = this.isAsyncFunction())) {
          var n2 = this.startNode();
          this.next(), i3 && this.next(), e5.declaration = this.parseFunction(n2, 5, false, i3);
        } else if (this.type === s2._class) {
          var r2 = this.startNode();
          e5.declaration = this.parseClass(r2, "nullableID");
        } else
          e5.declaration = this.parseMaybeAssign(), this.semicolon();
        return this.finishNode(e5, "ExportDefaultDeclaration");
      }
      if (this.shouldParseExportStatement())
        e5.declaration = this.parseStatement(null), "VariableDeclaration" === e5.declaration.type ? this.checkVariableExport(t3, e5.declaration.declarations) : this.checkExport(t3, e5.declaration.id.name, e5.declaration.id.start), e5.specifiers = [], e5.source = null;
      else {
        if (e5.declaration = null, e5.specifiers = this.parseExportSpecifiers(t3), this.eatContextual("from")) {
          if (this.type !== s2.string && this.unexpected(), e5.source = this.parseExprAtom(), this.type === this.assertToken || this.type === s2._with) {
            this.next();
            const t4 = this.parseImportAssertions();
            t4 && (e5.assertions = t4);
          }
        } else {
          for (var o2 = 0, a2 = e5.specifiers; o2 < a2.length; o2 += 1) {
            var l2 = a2[o2];
            this.checkUnreserved(l2.local), this.checkLocalExport(l2.local);
          }
          e5.source = null;
        }
        this.semicolon();
      }
      return this.finishNode(e5, "ExportNamedDeclaration");
    }
    parseImport(e5) {
      if (this.next(), this.type === s2.string ? (e5.specifiers = [], e5.source = this.parseExprAtom()) : (e5.specifiers = this.parseImportSpecifiers(), this.expectContextual("from"), e5.source = this.type === s2.string ? this.parseExprAtom() : this.unexpected()), this.type === this.assertToken || this.type == s2._with) {
        this.next();
        const t3 = this.parseImportAssertions();
        t3 && (e5.assertions = t3);
      }
      return this.semicolon(), this.finishNode(e5, "ImportDeclaration");
    }
    parseImportAssertions() {
      this._eat(s2.braceL);
      const e5 = this.parseAssertEntries();
      return this._eat(s2.braceR), e5;
    }
    parseAssertEntries() {
      const e5 = [], t3 = /* @__PURE__ */ new Set();
      do {
        if (this.type === s2.braceR)
          break;
        const i3 = this.startNode();
        let n2;
        n2 = this.type === s2.string ? this.parseLiteral(this.value) : this.parseIdent(true), this.next(), i3.key = n2, t3.has(i3.key.name) && this.raise(this.pos, "Duplicated key in assertions"), t3.add(i3.key.name), this.type !== s2.string && this.raise(this.pos, "Only string is supported as an assertion value"), i3.value = this.parseLiteral(this.value), e5.push(this.finishNode(i3, "ImportAttribute"));
      } while (this.eat(s2.comma));
      return e5;
    }
  };
}
function Bu(e4) {
  return Array.isArray(e4) ? e4.filter(Boolean) : e4 ? [e4] : [];
}
const zu = (e4) => ({ ecmaVersion: "latest", sourceType: "module", ...e4.acorn }), Fu = (e4) => [Vu, ...Bu(e4.acornInjectPlugins)], ju = (e4) => {
  var _a2;
  return true === e4.cache ? void 0 : ((_a2 = e4.cache) == null ? void 0 : _a2.cache) || e4.cache;
}, Uu = (e4) => {
  if (true === e4)
    return () => true;
  if ("function" == typeof e4)
    return (t2, ...s2) => !t2.startsWith("\0") && e4(t2, ...s2) || false;
  if (e4) {
    const t2 = /* @__PURE__ */ new Set(), s2 = [];
    for (const i2 of Bu(e4))
      i2 instanceof RegExp ? s2.push(i2) : t2.add(i2);
    return (e5, ...i2) => t2.has(e5) || s2.some((t3) => t3.test(e5));
  }
  return () => false;
}, Gu = (e4, t2, s2) => {
  const i2 = e4.inlineDynamicImports;
  return i2 && Zt('The "inlineDynamicImports" option is deprecated. Use the "output.inlineDynamicImports" option instead.', We, true, t2, s2), i2;
}, Wu = (e4) => {
  const t2 = e4.input;
  return null == t2 ? [] : "string" == typeof t2 ? [t2] : t2;
}, qu = (e4, t2, s2) => {
  const i2 = e4.manualChunks;
  return i2 && Zt('The "manualChunks" option is deprecated. Use the "output.manualChunks" option instead.', He, true, t2, s2), i2;
}, Hu = (e4, t2, s2) => {
  const i2 = e4.maxParallelFileReads;
  "number" == typeof i2 && Zt('The "maxParallelFileReads" option is deprecated. Use the "maxParallelFileOps" option instead.', "configuration-options/#maxparallelfileops", true, t2, s2);
  const n2 = e4.maxParallelFileOps ?? i2;
  return "number" == typeof n2 ? n2 <= 0 ? 1 / 0 : n2 : 20;
}, Ku = (e4, t2) => {
  const s2 = e4.moduleContext;
  if ("function" == typeof s2)
    return (e5) => s2(e5) ?? t2;
  if (s2) {
    const e5 = /* @__PURE__ */ Object.create(null);
    for (const [t3, i2] of Object.entries(s2))
      e5[R(t3)] = i2;
    return (s3) => e5[s3] ?? t2;
  }
  return () => t2;
}, Yu = (e4, t2, s2) => {
  const i2 = e4.preserveModules;
  return i2 && Zt('The "preserveModules" option is deprecated. Use the "output.preserveModules" option instead.', "configuration-options/#output-preservemodules", true, t2, s2), i2;
}, Xu = (e4) => {
  if (false === e4.treeshake)
    return false;
  const t2 = ru(e4.treeshake, iu, "treeshake", "configuration-options/#treeshake", "false, true, ");
  return { annotations: false !== t2.annotations, correctVarValueBeforeDeclaration: true === t2.correctVarValueBeforeDeclaration, manualPureFunctions: t2.manualPureFunctions ?? ye, moduleSideEffects: Qu(t2.moduleSideEffects), propertyReadSideEffects: "always" === t2.propertyReadSideEffects ? "always" : false !== t2.propertyReadSideEffects, tryCatchDeoptimization: false !== t2.tryCatchDeoptimization, unknownGlobalSideEffects: false !== t2.unknownGlobalSideEffects };
}, Qu = (e4) => {
  if ("boolean" == typeof e4)
    return () => e4;
  if ("no-external" === e4)
    return (e5, t2) => !t2;
  if ("function" == typeof e4)
    return (t2, s2) => !!t2.startsWith("\0") || false !== e4(t2, s2);
  if (Array.isArray(e4)) {
    const t2 = new Set(e4);
    return (e5) => t2.has(e5);
  }
  return e4 && Qe(jt("treeshake.moduleSideEffects", "configuration-options/#treeshake-modulesideeffects", 'please use one of false, "no-external", a function or an array')), () => true;
}, Zu = /[\u0000-\u001F"#$&*+,:;<=>?[\]^`{|}\u007F]/g, Ju = /^[a-z]:/i;
function ed(e4) {
  const t2 = Ju.exec(e4), s2 = t2 ? t2[0] : "";
  return s2 + e4.slice(s2.length).replace(Zu, "_");
}
const td = (e4, t2, s2) => {
  const { file: i2 } = e4;
  if ("string" == typeof i2) {
    if (t2)
      return Qe(jt("output.file", Be, 'you must set "output.dir" instead of "output.file" when using the "output.preserveModules" option'));
    if (!Array.isArray(s2.input))
      return Qe(jt("output.file", Be, 'you must set "output.dir" instead of "output.file" when providing named inputs'));
  }
  return i2;
}, sd = (e4) => {
  const t2 = e4.format;
  switch (t2) {
    case void 0:
    case "es":
    case "esm":
    case "module":
      return "es";
    case "cjs":
    case "commonjs":
      return "cjs";
    case "system":
    case "systemjs":
      return "system";
    case "amd":
    case "iife":
    case "umd":
      return t2;
    default:
      return Qe(jt("output.format", je, 'Valid values are "amd", "cjs", "system", "es", "iife" or "umd"', t2));
  }
}, id = (e4, t2) => {
  const s2 = (e4.inlineDynamicImports ?? t2.inlineDynamicImports) || false, { input: i2 } = t2;
  return s2 && (Array.isArray(i2) ? i2 : Object.keys(i2)).length > 1 ? Qe(jt("output.inlineDynamicImports", We, 'multiple inputs are not supported when "output.inlineDynamicImports" is true')) : s2;
}, nd = (e4, t2, s2) => {
  const i2 = (e4.preserveModules ?? s2.preserveModules) || false;
  if (i2) {
    if (t2)
      return Qe(jt("output.inlineDynamicImports", We, 'this option is not supported for "output.preserveModules"'));
    if (false === s2.preserveEntrySignatures)
      return Qe(jt("preserveEntrySignatures", "configuration-options/#preserveentrysignatures", 'setting this option to false is not supported for "output.preserveModules"'));
  }
  return i2;
}, rd = (e4, t2) => {
  const s2 = e4.preferConst;
  return null != s2 && Qt('The "output.preferConst" option is deprecated. Use the "output.generatedCode.constBindings" option instead.', "configuration-options/#output-generatedcode-constbindings", true, t2), !!s2;
}, od = (e4) => {
  const { preserveModulesRoot: t2 } = e4;
  if (null != t2)
    return R(t2);
}, ad = (e4) => {
  const t2 = { autoId: false, basePath: "", define: "define", forceJsExtensionForImports: false, ...e4.amd };
  return (t2.autoId || t2.basePath) && t2.id ? Qe(jt("output.amd.id", Ve, 'this option cannot be used together with "output.amd.autoId"/"output.amd.basePath"')) : t2.basePath && !t2.autoId ? Qe(jt("output.amd.basePath", "configuration-options/#output-amd-basepath", 'this option only works with "output.amd.autoId"')) : t2.autoId ? { autoId: true, basePath: t2.basePath, define: t2.define, forceJsExtensionForImports: t2.forceJsExtensionForImports } : { autoId: false, define: t2.define, forceJsExtensionForImports: t2.forceJsExtensionForImports, id: t2.id };
}, ld = (e4, t2) => {
  const s2 = e4[t2];
  return "function" == typeof s2 ? s2 : () => s2 || "";
}, cd = (e4, t2) => {
  const { dir: s2 } = e4;
  return "string" == typeof s2 && "string" == typeof t2 ? Qe(jt("output.dir", Be, 'you must set either "output.file" for a single-file build or "output.dir" when generating multiple chunks')) : s2;
}, hd = (e4, t2, s2) => {
  const i2 = e4.dynamicImportFunction;
  return i2 && (Qt('The "output.dynamicImportFunction" option is deprecated. Use the "renderDynamicImport" plugin hook instead.', "plugin-development/#renderdynamicimport", true, t2), "es" !== s2 && t2.onLog(Ae, jt("output.dynamicImportFunction", "configuration-options/#output-dynamicimportfunction", 'this option is ignored for formats other than "es"'))), i2;
}, ud = (e4, t2) => {
  const s2 = e4.entryFileNames;
  return null == s2 && t2.add("entryFileNames"), s2 ?? "[name].js";
};
function dd(e4, t2) {
  const s2 = e4.experimentalDeepDynamicChunkOptimization;
  return null != s2 && Qt('The "output.experimentalDeepDynamicChunkOptimization" option is deprecated as Rollup always runs the full chunking algorithm now. The option should be removed.', Ue, true, t2), s2 || false;
}
function pd(e4, t2) {
  const s2 = e4.exports;
  if (null == s2)
    t2.add("exports");
  else if (!["default", "named", "none", "auto"].includes(s2))
    return Qe({ code: ut, message: `"output.exports" must be "default", "named", "none", "auto", or left unspecified (defaults to "auto"), received "${s2}".`, url: De(ze) });
  return s2 || "auto";
}
const fd = (e4, t2) => {
  const s2 = ru(e4.generatedCode, nu, "output.generatedCode", "configuration-options/#output-generatedcode", "");
  return { arrowFunctions: true === s2.arrowFunctions, constBindings: true === s2.constBindings || t2, objectShorthand: true === s2.objectShorthand, reservedNamesAsProps: false !== s2.reservedNamesAsProps, symbols: true === s2.symbols };
}, md = (e4, t2) => {
  if (t2)
    return "";
  const s2 = e4.indent;
  return false === s2 ? "" : s2 ?? true;
}, gd = /* @__PURE__ */ new Set(["compat", "auto", "esModule", "default", "defaultOnly"]), yd = (e4) => {
  const t2 = e4.interop;
  if ("function" == typeof t2) {
    const e5 = /* @__PURE__ */ Object.create(null);
    let s2 = null;
    return (i2) => null === i2 ? s2 || xd(s2 = t2(i2)) : i2 in e5 ? e5[i2] : xd(e5[i2] = t2(i2));
  }
  return void 0 === t2 ? () => "default" : () => xd(t2);
}, xd = (e4) => gd.has(e4) ? e4 : Qe(jt("output.interop", qe, `use one of ${Array.from(gd, (e5) => JSON.stringify(e5)).join(", ")}`, e4)), Ed = (e4, t2, s2, i2) => {
  const n2 = e4.manualChunks || i2.manualChunks;
  if (n2) {
    if (t2)
      return Qe(jt("output.manualChunks", He, 'this option is not supported for "output.inlineDynamicImports"'));
    if (s2)
      return Qe(jt("output.manualChunks", He, 'this option is not supported for "output.preserveModules"'));
  }
  return n2 || {};
}, bd = (e4, t2, s2) => e4.minifyInternalExports ?? (s2 || "es" === t2 || "system" === t2), vd = (e4, t2, s2) => {
  const i2 = e4.namespaceToStringTag;
  return null != i2 ? (Qt('The "output.namespaceToStringTag" option is deprecated. Use the "output.generatedCode.symbols" option instead.', "configuration-options/#output-generatedcode-symbols", true, s2), i2) : t2.symbols || false;
}, Sd = (e4, t2) => {
  const s2 = e4.sourcemapFileNames;
  return null == s2 && t2.add("sourcemapFileNames"), s2;
}, Ad = (e4) => {
  const { sourcemapBaseUrl: t2 } = e4;
  if (t2)
    return function(e5) {
      try {
        new URL(e5);
      } catch {
        return false;
      }
      return true;
    }(t2) ? (s2 = t2).endsWith("/") ? s2 : s2 + "/" : Qe(jt("output.sourcemapBaseUrl", "configuration-options/#output-sourcemapbaseurl", `must be a valid URL, received ${JSON.stringify(t2)}`));
  var s2;
};
function kd(t2) {
  return async function(t3, s2) {
    const { options: i2, unsetOptions: n2 } = await async function(t4, s3) {
      if (!t4)
        throw new Error("You must supply an options object to rollup");
      const i3 = await async function(t5, s4) {
        const i4 = Pu("options", await ou(t5.plugins)), n4 = t5.logLevel || ke, r4 = Ou(i4, Xh(t5, n4), s4, n4);
        for (const o3 of i4) {
          const { name: i5, options: a3 } = o3, l2 = "handler" in a3 ? a3.handler : a3, c2 = await l2.call({ debug: Au(Ie, "PLUGIN_LOG", r4, i5, n4), error: (e4) => Qe(qt(Jh(e4), i5, { hook: "onLog" })), info: Au(ke, "PLUGIN_LOG", r4, i5, n4), meta: { rollupVersion: e, watchMode: s4 }, warn: Au(Ae, "PLUGIN_WARNING", r4, i5, n4) }, t5);
          c2 && (t5 = c2);
        }
        return t5;
      }(t4, s3), { options: n3, unsetOptions: r3 } = await async function(e4, t5) {
        const s4 = /* @__PURE__ */ new Set(), i4 = e4.context ?? "undefined", n4 = await ou(e4.plugins), r4 = e4.logLevel || ke, o3 = Ou(n4, Xh(e4, r4), t5, r4), a3 = e4.strictDeprecations || false, l2 = Hu(e4, o3, a3), c2 = { acorn: zu(e4), acornInjectPlugins: Fu(e4), cache: ju(e4), context: i4, experimentalCacheExpiry: e4.experimentalCacheExpiry ?? 10, experimentalLogSideEffects: e4.experimentalLogSideEffects || false, external: Uu(e4.external), inlineDynamicImports: Gu(e4, o3, a3), input: Wu(e4), logLevel: r4, makeAbsoluteExternalsRelative: e4.makeAbsoluteExternalsRelative ?? "ifRelativeSource", manualChunks: qu(e4, o3, a3), maxParallelFileOps: l2, maxParallelFileReads: l2, moduleContext: Ku(e4, i4), onLog: o3, onwarn: (e5) => o3(Ae, e5), perf: e4.perf || false, plugins: n4, preserveEntrySignatures: e4.preserveEntrySignatures ?? "exports-only", preserveModules: Yu(e4, o3, a3), preserveSymlinks: e4.preserveSymlinks || false, shimMissingExports: e4.shimMissingExports || false, strictDeprecations: a3, treeshake: Xu(e4) };
        return su(e4, [...Object.keys(c2), "watch"], "input options", o3, /^(output)$/), { options: c2, unsetOptions: s4 };
      }(i3, s3);
      return Id(n3.plugins, qh), { options: n3, unsetOptions: r3 };
    }(t3, null !== s2);
    !function(e4) {
      e4.perf ? ($o = /* @__PURE__ */ new Map(), Oo = _o, Do = Ro, e4.plugins = e4.plugins.map(To)) : (Oo = Ui, Do = Ui);
    }(i2);
    const r2 = new Ru(i2, s2), o2 = false !== t3.cache;
    t3.cache && (i2.cache = void 0, t3.cache = void 0);
    Oo("BUILD", 1), await Mu(r2.pluginDriver, async () => {
      try {
        Oo("initialize", 2), await r2.pluginDriver.hookParallel("buildStart", [i2]), Do("initialize", 2), await r2.build();
      } catch (e4) {
        const t4 = Object.keys(r2.watchFiles);
        throw t4.length > 0 && (e4.watchFiles = t4), await r2.pluginDriver.hookParallel("buildEnd", [e4]), await r2.pluginDriver.hookParallel("closeBundle", []), e4;
      }
      await r2.pluginDriver.hookParallel("buildEnd", []);
    }), Do("BUILD", 1);
    const a2 = { cache: o2 ? r2.getCache() : void 0, async close() {
      a2.closed || (a2.closed = true, await r2.pluginDriver.hookParallel("closeBundle", []));
    }, closed: false, generate: async (e4) => a2.closed ? Qe(Mt()) : wd(false, i2, n2, e4, r2), watchFiles: Object.keys(r2.watchFiles), write: async (e4) => a2.closed ? Qe(Mt()) : wd(true, i2, n2, e4, r2) };
    i2.perf && (a2.getTimings = Mo);
    return a2;
  }(t2, null);
}
function Id(e4, t2) {
  for (const [s2, i2] of e4.entries())
    i2.name || (i2.name = `${t2}${s2 + 1}`);
}
async function wd(e4, t2, s2, i2, n2) {
  const { options: r2, outputPluginDriver: o2, unsetOptions: a2 } = await async function(e5, t3, s3, i3) {
    if (!e5)
      throw new Error("You must supply an options object");
    const n3 = await ou(e5.plugins);
    Id(n3, Hh);
    const r3 = t3.createOutputPluginDriver(n3);
    return { ...await Pd(s3, i3, e5, r3), outputPluginDriver: r3 };
  }(i2, n2.pluginDriver, t2, s2);
  return Mu(0, async () => {
    const s3 = new Kl(r2, a2, t2, o2, n2), i3 = await s3.generate(e4);
    if (e4) {
      if (Oo("WRITE", 1), !r2.dir && !r2.file)
        return Qe({ code: St, message: 'You must specify "output.file" or "output.dir" for the build.', url: De(Be) });
      await Promise.all(Object.values(i3).map((e5) => n2.fileOperationQueue.run(() => async function(e6, t3) {
        const s4 = R(t3.dir || $(t3.file), e6.fileName);
        return await jh($(s4), { recursive: true }), Gh(s4, "asset" === e6.type ? e6.source : e6.code);
      }(e5, r2)))), await o2.hookParallel("writeBundle", [r2, i3]), Do("WRITE", 1);
    }
    return l2 = i3, { output: Object.values(l2).filter((e5) => Object.keys(e5).length > 0).sort((e5, t3) => $d(e5) - $d(t3)) };
    var l2;
  });
}
function Pd(e4, t2, s2, i2) {
  return async function(e5, t3, s3) {
    const i3 = new Set(s3), n2 = e5.compact || false, r2 = sd(e5), o2 = id(e5, t3), a2 = nd(e5, o2, t3), l2 = td(e5, a2, t3), c2 = rd(e5, t3), h2 = fd(e5, c2), u2 = { amd: ad(e5), assetFileNames: e5.assetFileNames ?? "assets/[name]-[hash][extname]", banner: ld(e5, "banner"), chunkFileNames: e5.chunkFileNames ?? "[name]-[hash].js", compact: n2, dir: cd(e5, l2), dynamicImportFunction: hd(e5, t3, r2), dynamicImportInCjs: e5.dynamicImportInCjs ?? true, entryFileNames: ud(e5, i3), esModule: e5.esModule ?? "if-default-prop", experimentalDeepDynamicChunkOptimization: dd(e5, t3), experimentalMinChunkSize: e5.experimentalMinChunkSize ?? 1, exports: pd(e5, i3), extend: e5.extend || false, externalImportAssertions: e5.externalImportAssertions ?? true, externalLiveBindings: e5.externalLiveBindings ?? true, file: l2, footer: ld(e5, "footer"), format: r2, freeze: e5.freeze ?? true, generatedCode: h2, globals: e5.globals || {}, hoistTransitiveImports: e5.hoistTransitiveImports ?? true, indent: md(e5, n2), inlineDynamicImports: o2, interop: yd(e5), intro: ld(e5, "intro"), manualChunks: Ed(e5, o2, a2, t3), minifyInternalExports: bd(e5, r2, n2), name: e5.name, namespaceToStringTag: vd(e5, h2, t3), noConflict: e5.noConflict || false, outro: ld(e5, "outro"), paths: e5.paths || {}, plugins: await ou(e5.plugins), preferConst: c2, preserveModules: a2, preserveModulesRoot: od(e5), sanitizeFileName: "function" == typeof e5.sanitizeFileName ? e5.sanitizeFileName : false === e5.sanitizeFileName ? (e6) => e6 : ed, sourcemap: e5.sourcemap || false, sourcemapBaseUrl: Ad(e5), sourcemapExcludeSources: e5.sourcemapExcludeSources || false, sourcemapFile: e5.sourcemapFile, sourcemapFileNames: Sd(e5, i3), sourcemapIgnoreList: "function" == typeof e5.sourcemapIgnoreList ? e5.sourcemapIgnoreList : false === e5.sourcemapIgnoreList ? () => false : (e6) => e6.includes("node_modules"), sourcemapPathTransform: e5.sourcemapPathTransform, strict: e5.strict ?? true, systemNullSetters: e5.systemNullSetters ?? true, validate: e5.validate || false };
    return su(e5, Object.keys(u2), "output options", t3.onLog), { options: u2, unsetOptions: i3 };
  }(i2.hookReduceArg0Sync("outputOptions", [s2], (e5, t3) => t3 || e5, (e5) => {
    const t3 = () => e5.error({ code: it, message: 'Cannot emit files or set asset sources in the "outputOptions" hook, use the "renderStart" hook instead.' });
    return { ...e5, emitFile: t3, setAssetSource: t3 };
  }), e4, t2);
}
var Cd;
function $d(e4) {
  return "asset" === e4.type ? Cd.ASSET : e4.isEntry ? Cd.ENTRY_CHUNK : Cd.SECONDARY_CHUNK;
}
!function(e4) {
  e4[e4.ENTRY_CHUNK = 0] = "ENTRY_CHUNK", e4[e4.SECONDARY_CHUNK = 1] = "SECONDARY_CHUNK", e4[e4.ASSET = 2] = "ASSET";
}(Cd || (Cd = {}));
function normalizeArray(parts, allowAboveRoot) {
  const res = [];
  for (var i2 = 0; i2 < parts.length; i2++) {
    const p2 = parts[i2];
    if (!p2 || p2 === ".")
      continue;
    if (p2 === "..") {
      if (res.length && res[res.length - 1] !== "..") {
        res.pop();
      } else if (allowAboveRoot) {
        res.push("..");
      }
    } else {
      res.push(p2);
    }
  }
  return res;
}
function absolutePath(path, basePath) {
  if (path.indexOf(":") > 0)
    return path;
  if (!path.startsWith("./"))
    return path;
  let resolvedPath = path;
  if (basePath && !resolvedPath.startsWith("/")) {
    resolvedPath = basePath + "/../" + path;
  }
  resolvedPath = normalizeArray(
    resolvedPath.split("/"),
    false
  ).join("/");
  return "/" + resolvedPath;
}
async function rollupTransform(path, source) {
  if (path.endsWith(".lib"))
    path += ".js";
  const parts = absolutePath("./parts/", path);
  const inputOptions = {
    input: path,
    shimMissingExports: false,
    treeshake: false,
    external(id2, importer) {
      id2 = absolutePath(id2, importer);
      return id2 != path && !id2.startsWith(parts);
    },
    plugins: [
      {
        name: "rollup-adapter",
        resolveId(importee, importer) {
          console.debug("resolveId:importee=" + importee + ",importer=" + importer);
          if (importee.endsWith(".xjs")) {
            importee = importee.substring(0, importee.length - ".xjs".length) + ".js";
          }
          if (!importee.endsWith(".js") && !importee.endsWith(".mjs")) {
            importee += ".js";
          }
          return absolutePath(importee, importer);
        },
        load(id2) {
          console.debug("load:id=" + id2);
          if (id2 == path) {
            return source;
          } else {
            return jsLibLoader(id2);
          }
        }
      }
      // commonjs(),
      // tsPlugin
    ]
  };
  const outputOptions = {
    format: "system",
    minifyInternalExports: false,
    //preserveModules:true,
    generatedCode: "es2015",
    chunkFileNames: "[name]",
    sanitizeFileName: false,
    // 如果为true, 'nop:utils'将被替换为/nop_utils
    manualChunks(id2) {
      return "app";
    }
  };
  const bundle = await kd(inputOptions);
  const generated = await bundle.generate(outputOptions);
  let { code } = generated.output[0];
  code = code.replace(/\'\.\/@nop\/utils\.js\'/g, "'@nop/utils'");
  return code;
}
export {
  rollupTransform
};
