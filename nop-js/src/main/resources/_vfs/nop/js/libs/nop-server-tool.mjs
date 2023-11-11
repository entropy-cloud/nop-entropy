/*
  @license
	Rollup.js v3.26.0
	Fri, 30 Jun 2023 04:42:44 GMT - commit 5365e5e09684d7b1dc645a4af40e513e8cdac560

	https://github.com/rollup/rollup

	Released under the MIT License.
*/
var e = "3.26.0";
function t(e3) {
  return e3 && e3.__esModule && Object.prototype.hasOwnProperty.call(e3, "default") ? e3.default : e3;
}
var s = { exports: {} };
!function(e3) {
  const t2 = ",".charCodeAt(0), s2 = ";".charCodeAt(0), i2 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/", n2 = new Uint8Array(64), r2 = new Uint8Array(128);
  for (let e4 = 0; e4 < i2.length; e4++) {
    const t3 = i2.charCodeAt(e4);
    n2[e4] = t3, r2[t3] = e4;
  }
  const o2 = "undefined" != typeof TextDecoder ? new TextDecoder() : "undefined" != typeof Buffer ? { decode: (e4) => Buffer.from(e4.buffer, e4.byteOffset, e4.byteLength).toString() } : { decode(e4) {
    let t3 = "";
    for (let s3 = 0; s3 < e4.length; s3++)
      t3 += String.fromCharCode(e4[s3]);
    return t3;
  } };
  function a2(e4) {
    const t3 = new Int32Array(5), s3 = [];
    let i3 = 0;
    do {
      const n3 = l2(e4, i3), r3 = [];
      let o3 = true, a3 = 0;
      t3[0] = 0;
      for (let s4 = i3; s4 < n3; s4++) {
        let i4;
        s4 = h2(e4, s4, t3, 0);
        const l3 = t3[0];
        l3 < a3 && (o3 = false), a3 = l3, c2(e4, s4, n3) ? (s4 = h2(e4, s4, t3, 1), s4 = h2(e4, s4, t3, 2), s4 = h2(e4, s4, t3, 3), c2(e4, s4, n3) ? (s4 = h2(e4, s4, t3, 4), i4 = [l3, t3[1], t3[2], t3[3], t3[4]]) : i4 = [l3, t3[1], t3[2], t3[3]]) : i4 = [l3], r3.push(i4);
      }
      o3 || u2(r3), s3.push(r3), i3 = n3 + 1;
    } while (i3 <= e4.length);
    return s3;
  }
  function l2(e4, t3) {
    const s3 = e4.indexOf(";", t3);
    return -1 === s3 ? e4.length : s3;
  }
  function h2(e4, t3, s3, i3) {
    let n3 = 0, o3 = 0, a3 = 0;
    do {
      const s4 = e4.charCodeAt(t3++);
      a3 = r2[s4], n3 |= (31 & a3) << o3, o3 += 5;
    } while (32 & a3);
    const l3 = 1 & n3;
    return n3 >>>= 1, l3 && (n3 = -2147483648 | -n3), s3[i3] += n3, t3;
  }
  function c2(e4, s3, i3) {
    return !(s3 >= i3) && e4.charCodeAt(s3) !== t2;
  }
  function u2(e4) {
    e4.sort(d2);
  }
  function d2(e4, t3) {
    return e4[0] - t3[0];
  }
  function p2(e4) {
    const i3 = new Int32Array(5), n3 = 16384, r3 = n3 - 36, a3 = new Uint8Array(n3), l3 = a3.subarray(0, r3);
    let h3 = 0, c3 = "";
    for (let u3 = 0; u3 < e4.length; u3++) {
      const d3 = e4[u3];
      if (u3 > 0 && (h3 === n3 && (c3 += o2.decode(a3), h3 = 0), a3[h3++] = s2), 0 !== d3.length) {
        i3[0] = 0;
        for (let e5 = 0; e5 < d3.length; e5++) {
          const s3 = d3[e5];
          h3 > r3 && (c3 += o2.decode(l3), a3.copyWithin(0, r3, h3), h3 -= r3), e5 > 0 && (a3[h3++] = t2), h3 = f2(a3, h3, i3, s3, 0), 1 !== s3.length && (h3 = f2(a3, h3, i3, s3, 1), h3 = f2(a3, h3, i3, s3, 2), h3 = f2(a3, h3, i3, s3, 3), 4 !== s3.length && (h3 = f2(a3, h3, i3, s3, 4)));
        }
      }
    }
    return c3 + o2.decode(a3.subarray(0, h3));
  }
  function f2(e4, t3, s3, i3, r3) {
    const o3 = i3[r3];
    let a3 = o3 - s3[r3];
    s3[r3] = o3, a3 = a3 < 0 ? -a3 << 1 | 1 : a3 << 1;
    do {
      let s4 = 31 & a3;
      a3 >>>= 5, a3 > 0 && (s4 |= 32), e4[t3++] = n2[s4];
    } while (a3 > 0);
    return t3;
  }
  e3.decode = a2, e3.encode = p2, Object.defineProperty(e3, "__esModule", { value: true });
}(s.exports);
var i = s.exports;
class n {
  constructor(e3) {
    this.bits = e3 instanceof n ? e3.bits.slice() : [];
  }
  add(e3) {
    this.bits[e3 >> 5] |= 1 << (31 & e3);
  }
  has(e3) {
    return !!(this.bits[e3 >> 5] & 1 << (31 & e3));
  }
}
let r = class e2 {
  constructor(e3, t2, s2) {
    this.start = e3, this.end = t2, this.original = s2, this.intro = "", this.outro = "", this.content = s2, this.storeName = false, this.edited = false, this.previous = null, this.next = null;
  }
  appendLeft(e3) {
    this.outro += e3;
  }
  appendRight(e3) {
    this.intro = this.intro + e3;
  }
  clone() {
    const t2 = new e2(this.start, this.end, this.original);
    return t2.intro = this.intro, t2.outro = this.outro, t2.content = this.content, t2.storeName = this.storeName, t2.edited = this.edited, t2;
  }
  contains(e3) {
    return this.start < e3 && e3 < this.end;
  }
  eachNext(e3) {
    let t2 = this;
    for (; t2; )
      e3(t2), t2 = t2.next;
  }
  eachPrevious(e3) {
    let t2 = this;
    for (; t2; )
      e3(t2), t2 = t2.previous;
  }
  edit(e3, t2, s2) {
    return this.content = e3, s2 || (this.intro = "", this.outro = ""), this.storeName = t2, this.edited = true, this;
  }
  prependLeft(e3) {
    this.outro = e3 + this.outro;
  }
  prependRight(e3) {
    this.intro = e3 + this.intro;
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
  trimEnd(e3) {
    if (this.outro = this.outro.replace(e3, ""), this.outro.length)
      return true;
    const t2 = this.content.replace(e3, "");
    return t2.length ? (t2 !== this.content && this.split(this.start + t2.length).edit("", void 0, true), true) : (this.edit("", void 0, true), this.intro = this.intro.replace(e3, ""), !!this.intro.length || void 0);
  }
  trimStart(e3) {
    if (this.intro = this.intro.replace(e3, ""), this.intro.length)
      return true;
    const t2 = this.content.replace(e3, "");
    return t2.length ? (t2 !== this.content && (this.split(this.end - t2.length), this.edit("", void 0, true)), true) : (this.edit("", void 0, true), this.outro = this.outro.replace(e3, ""), !!this.outro.length || void 0);
  }
};
function o() {
  return "undefined" != typeof window && "function" == typeof window.btoa ? (e3) => window.btoa(unescape(encodeURIComponent(e3))) : "function" == typeof Buffer ? (e3) => Buffer.from(e3, "utf-8").toString("base64") : () => {
    throw new Error("Unsupported environment: `window.btoa` or `Buffer` should be supported.");
  };
}
const a = o();
class l {
  constructor(e3) {
    this.version = 3, this.file = e3.file, this.sources = e3.sources, this.sourcesContent = e3.sourcesContent, this.names = e3.names, this.mappings = i.encode(e3.mappings), void 0 !== e3.x_google_ignoreList && (this.x_google_ignoreList = e3.x_google_ignoreList);
  }
  toString() {
    return JSON.stringify(this);
  }
  toUrl() {
    return "data:application/json;charset=utf-8;base64," + a(this.toString());
  }
}
function h(e3, t2) {
  const s2 = e3.split(/[/\\]/), i2 = t2.split(/[/\\]/);
  for (s2.pop(); s2[0] === i2[0]; )
    s2.shift(), i2.shift();
  if (s2.length) {
    let e4 = s2.length;
    for (; e4--; )
      s2[e4] = "..";
  }
  return s2.concat(i2).join("/");
}
const c = Object.prototype.toString;
function u(e3) {
  return "[object Object]" === c.call(e3);
}
function d(e3) {
  const t2 = e3.split("\n"), s2 = [];
  for (let e4 = 0, i2 = 0; e4 < t2.length; e4++)
    s2.push(i2), i2 += t2[e4].length + 1;
  return function(e4) {
    let t3 = 0, i2 = s2.length;
    for (; t3 < i2; ) {
      const n3 = t3 + i2 >> 1;
      e4 < s2[n3] ? i2 = n3 : t3 = n3 + 1;
    }
    const n2 = t3 - 1;
    return { line: n2, column: e4 - s2[n2] };
  };
}
class p {
  constructor(e3) {
    this.hires = e3, this.generatedCodeLine = 0, this.generatedCodeColumn = 0, this.raw = [], this.rawSegments = this.raw[this.generatedCodeLine] = [], this.pending = null;
  }
  addEdit(e3, t2, s2, i2) {
    if (t2.length) {
      const t3 = [this.generatedCodeColumn, e3, s2.line, s2.column];
      i2 >= 0 && t3.push(i2), this.rawSegments.push(t3);
    } else
      this.pending && this.rawSegments.push(this.pending);
    this.advance(t2), this.pending = null;
  }
  addUneditedChunk(e3, t2, s2, i2, n2) {
    let r2 = t2.start, o2 = true;
    for (; r2 < t2.end; )
      (this.hires || o2 || n2.has(r2)) && this.rawSegments.push([this.generatedCodeColumn, e3, i2.line, i2.column]), "\n" === s2[r2] ? (i2.line += 1, i2.column = 0, this.generatedCodeLine += 1, this.raw[this.generatedCodeLine] = this.rawSegments = [], this.generatedCodeColumn = 0, o2 = true) : (i2.column += 1, this.generatedCodeColumn += 1, o2 = false), r2 += 1;
    this.pending = null;
  }
  advance(e3) {
    if (!e3)
      return;
    const t2 = e3.split("\n");
    if (t2.length > 1) {
      for (let e4 = 0; e4 < t2.length - 1; e4++)
        this.generatedCodeLine++, this.raw[this.generatedCodeLine] = this.rawSegments = [];
      this.generatedCodeColumn = 0;
    }
    this.generatedCodeColumn += t2[t2.length - 1].length;
  }
}
const f = "\n", m = { insertLeft: false, insertRight: false, storeName: false };
class g {
  constructor(e3, t2 = {}) {
    const s2 = new r(0, e3.length, e3);
    Object.defineProperties(this, { original: { writable: true, value: e3 }, outro: { writable: true, value: "" }, intro: { writable: true, value: "" }, firstChunk: { writable: true, value: s2 }, lastChunk: { writable: true, value: s2 }, lastSearchedChunk: { writable: true, value: s2 }, byStart: { writable: true, value: {} }, byEnd: { writable: true, value: {} }, filename: { writable: true, value: t2.filename }, indentExclusionRanges: { writable: true, value: t2.indentExclusionRanges }, sourcemapLocations: { writable: true, value: new n() }, storedNames: { writable: true, value: {} }, indentStr: { writable: true, value: void 0 }, ignoreList: { writable: true, value: t2.ignoreList } }), this.byStart[0] = s2, this.byEnd[e3.length] = s2;
  }
  addSourcemapLocation(e3) {
    this.sourcemapLocations.add(e3);
  }
  append(e3) {
    if ("string" != typeof e3)
      throw new TypeError("outro content must be a string");
    return this.outro += e3, this;
  }
  appendLeft(e3, t2) {
    if ("string" != typeof t2)
      throw new TypeError("inserted content must be a string");
    this._split(e3);
    const s2 = this.byEnd[e3];
    return s2 ? s2.appendLeft(t2) : this.intro += t2, this;
  }
  appendRight(e3, t2) {
    if ("string" != typeof t2)
      throw new TypeError("inserted content must be a string");
    this._split(e3);
    const s2 = this.byStart[e3];
    return s2 ? s2.appendRight(t2) : this.outro += t2, this;
  }
  clone() {
    const e3 = new g(this.original, { filename: this.filename });
    let t2 = this.firstChunk, s2 = e3.firstChunk = e3.lastSearchedChunk = t2.clone();
    for (; t2; ) {
      e3.byStart[s2.start] = s2, e3.byEnd[s2.end] = s2;
      const i2 = t2.next, n2 = i2 && i2.clone();
      n2 && (s2.next = n2, n2.previous = s2, s2 = n2), t2 = i2;
    }
    return e3.lastChunk = s2, this.indentExclusionRanges && (e3.indentExclusionRanges = this.indentExclusionRanges.slice()), e3.sourcemapLocations = new n(this.sourcemapLocations), e3.intro = this.intro, e3.outro = this.outro, e3;
  }
  generateDecodedMap(e3) {
    e3 = e3 || {};
    const t2 = Object.keys(this.storedNames), s2 = new p(e3.hires), i2 = d(this.original);
    return this.intro && s2.advance(this.intro), this.firstChunk.eachNext((e4) => {
      const n2 = i2(e4.start);
      e4.intro.length && s2.advance(e4.intro), e4.edited ? s2.addEdit(0, e4.content, n2, e4.storeName ? t2.indexOf(e4.original) : -1) : s2.addUneditedChunk(0, e4, this.original, n2, this.sourcemapLocations), e4.outro.length && s2.advance(e4.outro);
    }), { file: e3.file ? e3.file.split(/[/\\]/).pop() : void 0, sources: [e3.source ? h(e3.file || "", e3.source) : e3.file || ""], sourcesContent: e3.includeContent ? [this.original] : void 0, names: t2, mappings: s2.raw, x_google_ignoreList: this.ignoreList ? [0] : void 0 };
  }
  generateMap(e3) {
    return new l(this.generateDecodedMap(e3));
  }
  _ensureindentStr() {
    void 0 === this.indentStr && (this.indentStr = function(e3) {
      const t2 = e3.split("\n"), s2 = t2.filter((e4) => /^\t+/.test(e4)), i2 = t2.filter((e4) => /^ {2,}/.test(e4));
      if (0 === s2.length && 0 === i2.length)
        return null;
      if (s2.length >= i2.length)
        return "	";
      const n2 = i2.reduce((e4, t3) => {
        const s3 = /^ +/.exec(t3)[0].length;
        return Math.min(s3, e4);
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
  indent(e3, t2) {
    const s2 = /^[^\r\n]/gm;
    if (u(e3) && (t2 = e3, e3 = void 0), void 0 === e3 && (this._ensureindentStr(), e3 = this.indentStr || "	"), "" === e3)
      return this;
    const i2 = {};
    if ((t2 = t2 || {}).exclude) {
      ("number" == typeof t2.exclude[0] ? [t2.exclude] : t2.exclude).forEach((e4) => {
        for (let t3 = e4[0]; t3 < e4[1]; t3 += 1)
          i2[t3] = true;
      });
    }
    let n2 = false !== t2.indentStart;
    const r2 = (t3) => n2 ? `${e3}${t3}` : (n2 = true, t3);
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
            "\n" === t4 ? n2 = true : "\r" !== t4 && n2 && (n2 = false, o2 === a2.start || (this._splitChunk(a2, o2), a2 = a2.next), a2.prependRight(e3));
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
  insertLeft(e3, t2) {
    return m.insertLeft || (console.warn("magicString.insertLeft(...) is deprecated. Use magicString.appendLeft(...) instead"), m.insertLeft = true), this.appendLeft(e3, t2);
  }
  insertRight(e3, t2) {
    return m.insertRight || (console.warn("magicString.insertRight(...) is deprecated. Use magicString.prependRight(...) instead"), m.insertRight = true), this.prependRight(e3, t2);
  }
  move(e3, t2, s2) {
    if (s2 >= e3 && s2 <= t2)
      throw new Error("Cannot move a selection inside itself");
    this._split(e3), this._split(t2), this._split(s2);
    const i2 = this.byStart[e3], n2 = this.byEnd[t2], r2 = i2.previous, o2 = n2.next, a2 = this.byStart[s2];
    if (!a2 && n2 === this.lastChunk)
      return this;
    const l2 = a2 ? a2.previous : this.lastChunk;
    return r2 && (r2.next = o2), o2 && (o2.previous = r2), l2 && (l2.next = i2), a2 && (a2.previous = n2), i2.previous || (this.firstChunk = n2.next), n2.next || (this.lastChunk = i2.previous, this.lastChunk.next = null), i2.previous = l2, n2.next = a2 || null, l2 || (this.firstChunk = i2), a2 || (this.lastChunk = n2), this;
  }
  overwrite(e3, t2, s2, i2) {
    return i2 = i2 || {}, this.update(e3, t2, s2, { ...i2, overwrite: !i2.contentOnly });
  }
  update(e3, t2, s2, i2) {
    if ("string" != typeof s2)
      throw new TypeError("replacement content must be a string");
    for (; e3 < 0; )
      e3 += this.original.length;
    for (; t2 < 0; )
      t2 += this.original.length;
    if (t2 > this.original.length)
      throw new Error("end is out of bounds");
    if (e3 === t2)
      throw new Error("Cannot overwrite a zero-length range – use appendLeft or prependRight instead");
    this._split(e3), this._split(t2), true === i2 && (m.storeName || (console.warn("The final argument to magicString.overwrite(...) should be an options object. See https://github.com/rich-harris/magic-string"), m.storeName = true), i2 = { storeName: true });
    const n2 = void 0 !== i2 && i2.storeName, o2 = void 0 !== i2 && i2.overwrite;
    if (n2) {
      const s3 = this.original.slice(e3, t2);
      Object.defineProperty(this.storedNames, s3, { writable: true, value: true, enumerable: true });
    }
    const a2 = this.byStart[e3], l2 = this.byEnd[t2];
    if (a2) {
      let e4 = a2;
      for (; e4 !== l2; ) {
        if (e4.next !== this.byStart[e4.end])
          throw new Error("Cannot overwrite across a split point");
        e4 = e4.next, e4.edit("", false);
      }
      a2.edit(s2, n2, !o2);
    } else {
      const i3 = new r(e3, t2, "").edit(s2, n2);
      l2.next = i3, i3.previous = l2;
    }
    return this;
  }
  prepend(e3) {
    if ("string" != typeof e3)
      throw new TypeError("outro content must be a string");
    return this.intro = e3 + this.intro, this;
  }
  prependLeft(e3, t2) {
    if ("string" != typeof t2)
      throw new TypeError("inserted content must be a string");
    this._split(e3);
    const s2 = this.byEnd[e3];
    return s2 ? s2.prependLeft(t2) : this.intro = t2 + this.intro, this;
  }
  prependRight(e3, t2) {
    if ("string" != typeof t2)
      throw new TypeError("inserted content must be a string");
    this._split(e3);
    const s2 = this.byStart[e3];
    return s2 ? s2.prependRight(t2) : this.outro = t2 + this.outro, this;
  }
  remove(e3, t2) {
    for (; e3 < 0; )
      e3 += this.original.length;
    for (; t2 < 0; )
      t2 += this.original.length;
    if (e3 === t2)
      return this;
    if (e3 < 0 || t2 > this.original.length)
      throw new Error("Character is out of bounds");
    if (e3 > t2)
      throw new Error("end must be greater than start");
    this._split(e3), this._split(t2);
    let s2 = this.byStart[e3];
    for (; s2; )
      s2.intro = "", s2.outro = "", s2.edit(""), s2 = t2 > s2.end ? this.byStart[s2.end] : null;
    return this;
  }
  lastChar() {
    if (this.outro.length)
      return this.outro[this.outro.length - 1];
    let e3 = this.lastChunk;
    do {
      if (e3.outro.length)
        return e3.outro[e3.outro.length - 1];
      if (e3.content.length)
        return e3.content[e3.content.length - 1];
      if (e3.intro.length)
        return e3.intro[e3.intro.length - 1];
    } while (e3 = e3.previous);
    return this.intro.length ? this.intro[this.intro.length - 1] : "";
  }
  lastLine() {
    let e3 = this.outro.lastIndexOf(f);
    if (-1 !== e3)
      return this.outro.substr(e3 + 1);
    let t2 = this.outro, s2 = this.lastChunk;
    do {
      if (s2.outro.length > 0) {
        if (e3 = s2.outro.lastIndexOf(f), -1 !== e3)
          return s2.outro.substr(e3 + 1) + t2;
        t2 = s2.outro + t2;
      }
      if (s2.content.length > 0) {
        if (e3 = s2.content.lastIndexOf(f), -1 !== e3)
          return s2.content.substr(e3 + 1) + t2;
        t2 = s2.content + t2;
      }
      if (s2.intro.length > 0) {
        if (e3 = s2.intro.lastIndexOf(f), -1 !== e3)
          return s2.intro.substr(e3 + 1) + t2;
        t2 = s2.intro + t2;
      }
    } while (s2 = s2.previous);
    return e3 = this.intro.lastIndexOf(f), -1 !== e3 ? this.intro.substr(e3 + 1) + t2 : this.intro + t2;
  }
  slice(e3 = 0, t2 = this.original.length) {
    for (; e3 < 0; )
      e3 += this.original.length;
    for (; t2 < 0; )
      t2 += this.original.length;
    let s2 = "", i2 = this.firstChunk;
    for (; i2 && (i2.start > e3 || i2.end <= e3); ) {
      if (i2.start < t2 && i2.end >= t2)
        return s2;
      i2 = i2.next;
    }
    if (i2 && i2.edited && i2.start !== e3)
      throw new Error(`Cannot use replaced character ${e3} as slice start anchor.`);
    const n2 = i2;
    for (; i2; ) {
      !i2.intro || n2 === i2 && i2.start !== e3 || (s2 += i2.intro);
      const r2 = i2.start < t2 && i2.end >= t2;
      if (r2 && i2.edited && i2.end !== t2)
        throw new Error(`Cannot use replaced character ${t2} as slice end anchor.`);
      const o2 = n2 === i2 ? e3 - i2.start : 0, a2 = r2 ? i2.content.length + t2 - i2.end : i2.content.length;
      if (s2 += i2.content.slice(o2, a2), !i2.outro || r2 && i2.end !== t2 || (s2 += i2.outro), r2)
        break;
      i2 = i2.next;
    }
    return s2;
  }
  snip(e3, t2) {
    const s2 = this.clone();
    return s2.remove(0, e3), s2.remove(t2, s2.original.length), s2;
  }
  _split(e3) {
    if (this.byStart[e3] || this.byEnd[e3])
      return;
    let t2 = this.lastSearchedChunk;
    const s2 = e3 > t2.end;
    for (; t2; ) {
      if (t2.contains(e3))
        return this._splitChunk(t2, e3);
      t2 = s2 ? this.byStart[t2.end] : this.byEnd[t2.start];
    }
  }
  _splitChunk(e3, t2) {
    if (e3.edited && e3.content.length) {
      const s3 = d(this.original)(t2);
      throw new Error(`Cannot split a chunk that has already been edited (${s3.line}:${s3.column} – "${e3.original}")`);
    }
    const s2 = e3.split(t2);
    return this.byEnd[t2] = e3, this.byStart[t2] = s2, this.byEnd[s2.end] = s2, e3 === this.lastChunk && (this.lastChunk = s2), this.lastSearchedChunk = e3, true;
  }
  toString() {
    let e3 = this.intro, t2 = this.firstChunk;
    for (; t2; )
      e3 += t2.toString(), t2 = t2.next;
    return e3 + this.outro;
  }
  isEmpty() {
    let e3 = this.firstChunk;
    do {
      if (e3.intro.length && e3.intro.trim() || e3.content.length && e3.content.trim() || e3.outro.length && e3.outro.trim())
        return false;
    } while (e3 = e3.next);
    return true;
  }
  length() {
    let e3 = this.firstChunk, t2 = 0;
    do {
      t2 += e3.intro.length + e3.content.length + e3.outro.length;
    } while (e3 = e3.next);
    return t2;
  }
  trimLines() {
    return this.trim("[\\r\\n]");
  }
  trim(e3) {
    return this.trimStart(e3).trimEnd(e3);
  }
  trimEndAborted(e3) {
    const t2 = new RegExp((e3 || "\\s") + "+$");
    if (this.outro = this.outro.replace(t2, ""), this.outro.length)
      return true;
    let s2 = this.lastChunk;
    do {
      const e4 = s2.end, i2 = s2.trimEnd(t2);
      if (s2.end !== e4 && (this.lastChunk === s2 && (this.lastChunk = s2.next), this.byEnd[s2.end] = s2, this.byStart[s2.next.start] = s2.next, this.byEnd[s2.next.end] = s2.next), i2)
        return true;
      s2 = s2.previous;
    } while (s2);
    return false;
  }
  trimEnd(e3) {
    return this.trimEndAborted(e3), this;
  }
  trimStartAborted(e3) {
    const t2 = new RegExp("^" + (e3 || "\\s") + "+");
    if (this.intro = this.intro.replace(t2, ""), this.intro.length)
      return true;
    let s2 = this.firstChunk;
    do {
      const e4 = s2.end, i2 = s2.trimStart(t2);
      if (s2.end !== e4 && (s2 === this.lastChunk && (this.lastChunk = s2.next), this.byEnd[s2.end] = s2, this.byStart[s2.next.start] = s2.next, this.byEnd[s2.next.end] = s2.next), i2)
        return true;
      s2 = s2.next;
    } while (s2);
    return false;
  }
  trimStart(e3) {
    return this.trimStartAborted(e3), this;
  }
  hasChanged() {
    return this.original !== this.toString();
  }
  _replaceRegexp(e3, t2) {
    function s2(e4, s3) {
      return "string" == typeof t2 ? t2.replace(/\$(\$|&|\d+)/g, (t3, s4) => {
        if ("$" === s4)
          return "$";
        if ("&" === s4)
          return e4[0];
        return +s4 < e4.length ? e4[+s4] : `$${s4}`;
      }) : t2(...e4, e4.index, s3, e4.groups);
    }
    if (e3.global) {
      (function(e4, t3) {
        let s3;
        const i2 = [];
        for (; s3 = e4.exec(t3); )
          i2.push(s3);
        return i2;
      })(e3, this.original).forEach((e4) => {
        null != e4.index && this.overwrite(e4.index, e4.index + e4[0].length, s2(e4, this.original));
      });
    } else {
      const t3 = this.original.match(e3);
      t3 && null != t3.index && this.overwrite(t3.index, t3.index + t3[0].length, s2(t3, this.original));
    }
    return this;
  }
  _replaceString(e3, t2) {
    const { original: s2 } = this, i2 = s2.indexOf(e3);
    return -1 !== i2 && this.overwrite(i2, i2 + e3.length, t2), this;
  }
  replace(e3, t2) {
    return "string" == typeof e3 ? this._replaceString(e3, t2) : this._replaceRegexp(e3, t2);
  }
  _replaceAllString(e3, t2) {
    const { original: s2 } = this, i2 = e3.length;
    for (let n2 = s2.indexOf(e3); -1 !== n2; n2 = s2.indexOf(e3, n2 + i2))
      this.overwrite(n2, n2 + i2, t2);
    return this;
  }
  replaceAll(e3, t2) {
    if ("string" == typeof e3)
      return this._replaceAllString(e3, t2);
    if (!e3.global)
      throw new TypeError("MagicString.prototype.replaceAll called with a non-global RegExp argument");
    return this._replaceRegexp(e3, t2);
  }
}
const y = Object.prototype.hasOwnProperty;
const x = /^(?:\/|(?:[A-Za-z]:)?[/\\|])/, E = /^\.?\.\//, b = /\\/g, v = /[/\\]/, S = /\.[^.]+$/;
function A(e3) {
  return x.test(e3);
}
function k(e3) {
  return E.test(e3);
}
function I(e3) {
  return e3.replace(b, "/");
}
function w(e3) {
  return e3.split(v).pop() || "";
}
function P(e3) {
  const t2 = /[/\\][^/\\]*$/.exec(e3);
  if (!t2)
    return ".";
  return e3.slice(0, -t2[0].length) || "/";
}
function C(e3) {
  const t2 = S.exec(w(e3));
  return t2 ? t2[0] : "";
}
function $(e3, t2) {
  const s2 = e3.split(v).filter(Boolean), i2 = t2.split(v).filter(Boolean);
  for ("." === s2[0] && s2.shift(), "." === i2[0] && i2.shift(); s2[0] && i2[0] && s2[0] === i2[0]; )
    s2.shift(), i2.shift();
  for (; ".." === i2[0] && s2.length > 0; )
    i2.shift(), s2.pop();
  for (; s2.pop(); )
    i2.unshift("..");
  return i2.join("/");
}
function N(...e3) {
  const t2 = e3.shift();
  if (!t2)
    return "/";
  let s2 = t2.split(v);
  for (const t3 of e3)
    if (A(t3))
      s2 = t3.split(v);
    else {
      const e4 = t3.split(v);
      for (; "." === e4[0] || ".." === e4[0]; ) {
        ".." === e4.shift() && s2.pop();
      }
      s2.push(...e4);
    }
  return s2.join("/");
}
const _ = /[\n\r'\\\u2028\u2029]/, R = /([\n\r'\u2028\u2029])/g, O = /\\/g;
function D(e3) {
  return _.test(e3) ? e3.replace(O, "\\\\").replace(R, "\\$1") : e3;
}
function L(e3) {
  const t2 = w(e3);
  return t2.slice(0, Math.max(0, t2.length - C(e3).length));
}
function T(e3) {
  return A(e3) ? $(N(), e3) : e3;
}
function M(e3) {
  return "/" === e3[0] || "." === e3[0] && ("/" === e3[1] || "." === e3[1]) || A(e3);
}
const V = /^(\.\.\/)*\.\.$/;
function B(e3, t2, s2, i2) {
  let n2 = I($(P(e3), t2));
  if (s2 && n2.endsWith(".js") && (n2 = n2.slice(0, -3)), i2) {
    if ("" === n2)
      return "../" + w(t2);
    if (V.test(n2))
      return [...n2.split("/"), "..", w(t2)].join("/");
  }
  return n2 ? n2.startsWith("..") ? n2 : "./" + n2 : ".";
}
class z {
  constructor(e3, t2, s2) {
    this.options = t2, this.inputBase = s2, this.defaultVariableName = "", this.namespaceVariableName = "", this.variableName = "", this.fileName = null, this.importAssertions = null, this.id = e3.id, this.moduleInfo = e3.info, this.renormalizeRenderPath = e3.renormalizeRenderPath, this.suggestedVariableName = e3.suggestedVariableName;
  }
  getFileName() {
    if (this.fileName)
      return this.fileName;
    const { paths: e3 } = this.options;
    return this.fileName = ("function" == typeof e3 ? e3(this.id) : e3[this.id]) || (this.renormalizeRenderPath ? I($(this.inputBase, this.id)) : this.id);
  }
  getImportAssertions(e3) {
    return this.importAssertions || (this.importAssertions = function(e4, { getObject: t2 }) {
      if (!e4)
        return null;
      const s2 = Object.entries(e4).map(([e5, t3]) => [e5, `'${t3}'`]);
      if (s2.length > 0)
        return t2(s2, { lineBreakIndent: null });
      return null;
    }("es" === this.options.format && this.options.externalImportAssertions && this.moduleInfo.assertions, e3));
  }
  getImportPath(e3) {
    return D(this.renormalizeRenderPath ? B(e3, this.getFileName(), "amd" === this.options.format, false) : this.getFileName());
  }
}
function F(e3, t2, s2) {
  const i2 = e3.get(t2);
  if (void 0 !== i2)
    return i2;
  const n2 = s2();
  return e3.set(t2, n2), n2;
}
function j() {
  return /* @__PURE__ */ new Set();
}
function U() {
  return [];
}
const G = Symbol("Unknown Key"), W = Symbol("Unknown Non-Accessor Key"), q = Symbol("Unknown Integer"), H = Symbol("Symbol.toStringTag"), K = [], Y = [G], X = [W], Q = [q], Z = Symbol("Entities");
class J {
  constructor() {
    this.entityPaths = Object.create(null, { [Z]: { value: /* @__PURE__ */ new Set() } });
  }
  trackEntityAtPathAndGetIfTracked(e3, t2) {
    const s2 = this.getEntities(e3);
    return !!s2.has(t2) || (s2.add(t2), false);
  }
  withTrackedEntityAtPath(e3, t2, s2, i2) {
    const n2 = this.getEntities(e3);
    if (n2.has(t2))
      return i2;
    n2.add(t2);
    const r2 = s2();
    return n2.delete(t2), r2;
  }
  getEntities(e3) {
    let t2 = this.entityPaths;
    for (const s2 of e3)
      t2 = t2[s2] = t2[s2] || Object.create(null, { [Z]: { value: /* @__PURE__ */ new Set() } });
    return t2[Z];
  }
}
const ee = new J();
class te {
  constructor() {
    this.entityPaths = Object.create(null, { [Z]: { value: /* @__PURE__ */ new Map() } });
  }
  trackEntityAtPathAndGetIfTracked(e3, t2, s2) {
    let i2 = this.entityPaths;
    for (const t3 of e3)
      i2 = i2[t3] = i2[t3] || Object.create(null, { [Z]: { value: /* @__PURE__ */ new Map() } });
    const n2 = F(i2[Z], t2, j);
    return !!n2.has(s2) || (n2.add(s2), false);
  }
}
const se = Symbol("Unknown Value"), ie = Symbol("Unknown Truthy Value");
class ne {
  constructor() {
    this.included = false;
  }
  deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2) {
    ae(e3);
  }
  deoptimizePath(e3) {
  }
  getLiteralValueAtPath(e3, t2, s2) {
    return se;
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    return oe;
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return true;
  }
  include(e3, t2, s2) {
    this.included = true;
  }
  includeCallArguments(e3, t2) {
    for (const s2 of t2)
      s2.include(e3, false);
  }
  shouldBeIncluded(e3) {
    return true;
  }
}
const re = new class extends ne {
}(), oe = [re, false], ae = (e3) => {
  for (const t2 of e3.args)
    t2 == null ? void 0 : t2.deoptimizePath(Y);
}, le = { args: [null], type: 0 }, he = { args: [null, re], type: 1 }, ce = { args: [null], type: 2, withNew: false };
class ue extends ne {
  constructor(e3) {
    super(), this.name = e3, this.alwaysRendered = false, this.forbiddenNames = null, this.initReached = false, this.isId = false, this.isReassigned = false, this.kind = null, this.renderBaseName = null, this.renderName = null;
  }
  addReference(e3) {
  }
  forbidName(e3) {
    (this.forbiddenNames || (this.forbiddenNames = /* @__PURE__ */ new Set())).add(e3);
  }
  getBaseVariableName() {
    return this.renderBaseName || this.renderName || this.name;
  }
  getName(e3, t2) {
    if (t2 == null ? void 0 : t2(this))
      return this.name;
    const s2 = this.renderName || this.name;
    return this.renderBaseName ? `${this.renderBaseName}${e3(s2)}` : s2;
  }
  hasEffectsOnInteractionAtPath(e3, { type: t2 }, s2) {
    return 0 !== t2 || e3.length > 0;
  }
  include() {
    this.included = true;
  }
  markCalledFromTryStatement() {
  }
  setRenderNames(e3, t2) {
    this.renderBaseName = e3, this.renderName = t2;
  }
}
class de extends ue {
  constructor(e3, t2) {
    super(t2), this.referenced = false, this.module = e3, this.isNamespace = "*" === t2;
  }
  addReference(e3) {
    this.referenced = true, "default" !== this.name && "*" !== this.name || this.module.suggestName(e3.name);
  }
  hasEffectsOnInteractionAtPath(e3, { type: t2 }) {
    return 0 !== t2 || e3.length > (this.isNamespace ? 1 : 0);
  }
  include() {
    this.included || (this.included = true, this.module.used = true);
  }
}
const pe = Object.freeze(/* @__PURE__ */ Object.create(null)), fe = Object.freeze({}), me = Object.freeze([]), ge = Object.freeze(new class extends Set {
  add() {
    throw new Error("Cannot add to empty set");
  }
}());
var ye = /* @__PURE__ */ new Set(["await", "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete", "do", "else", "enum", "eval", "export", "extends", "false", "finally", "for", "function", "if", "implements", "import", "in", "instanceof", "interface", "let", "NaN", "new", "null", "package", "private", "protected", "public", "return", "static", "super", "switch", "this", "throw", "true", "try", "typeof", "undefined", "var", "void", "while", "with", "yield"]);
const xe = /[^\w$]/g, Ee = (e3) => ((e4) => /\d/.test(e4[0]))(e3) || ye.has(e3) || "arguments" === e3;
function be(e3) {
  return e3 = e3.replace(/-(\w)/g, (e4, t2) => t2.toUpperCase()).replace(xe, "_"), Ee(e3) && (e3 = `_${e3}`), e3 || "_";
}
const ve = "warn", Se = "info", Ae = "debug", ke = { [Ae]: 0, [Se]: 1, silent: 3, [ve]: 2 };
function Ie(e3, t2) {
  return e3.start <= t2 && t2 < e3.end;
}
function we(e3, t2, s2) {
  return function(e4, t3 = {}) {
    const { offsetLine: s3 = 0, offsetColumn: i2 = 0 } = t3;
    let n2 = 0;
    const r2 = e4.split("\n").map((e5, t4) => {
      const s4 = n2 + e5.length + 1, i3 = { start: n2, end: s4, line: t4 };
      return n2 = s4, i3;
    });
    let o2 = 0;
    return function(t4, n3) {
      if ("string" == typeof t4 && (t4 = e4.indexOf(t4, n3 ?? 0)), -1 === t4)
        return;
      let a2 = r2[o2];
      const l2 = t4 >= a2.end ? 1 : -1;
      for (; a2; ) {
        if (Ie(a2, t4))
          return { line: s3 + a2.line, column: i2 + t4 - a2.start, character: t4 };
        o2 += l2, a2 = r2[o2];
      }
    };
  }(e3, s2)(t2, s2 && s2.startIndex);
}
function Pe(e3) {
  return e3.replace(/^\t+/, (e4) => e4.split("	").join("  "));
}
const Ce = 120, $e = 10, Ne = "...";
function _e(e3, t2, s2) {
  let i2 = e3.split("\n");
  if (t2 > i2.length)
    return "";
  const n2 = Math.max(Pe(i2[t2 - 1].slice(0, s2)).length + $e + Ne.length, Ce), r2 = Math.max(0, t2 - 3);
  let o2 = Math.min(t2 + 2, i2.length);
  for (i2 = i2.slice(r2, o2); !/\S/.test(i2[i2.length - 1]); )
    i2.pop(), o2 -= 1;
  const a2 = String(o2).length;
  return i2.map((e4, i3) => {
    const o3 = r2 + i3 + 1 === t2;
    let l2 = String(i3 + r2 + 1);
    for (; l2.length < a2; )
      l2 = ` ${l2}`;
    let h2 = Pe(e4);
    if (h2.length > n2 && (h2 = `${h2.slice(0, n2 - Ne.length)}${Ne}`), o3) {
      const t3 = function(e5) {
        let t4 = "";
        for (; e5--; )
          t4 += " ";
        return t4;
      }(a2 + 2 + Pe(e4.slice(0, s2)).length) + "^";
      return `${l2}: ${h2}
${t3}`;
    }
    return `${l2}: ${h2}`;
  }).join("\n");
}
function Re(e3, t2) {
  const s2 = e3.length <= 1, i2 = e3.map((e4) => `"${e4}"`);
  let n2 = s2 ? i2[0] : `${i2.slice(0, -1).join(", ")} and ${i2.slice(-1)[0]}`;
  return t2 && (n2 += ` ${s2 ? t2[0] : t2[1]}`), n2;
}
function Oe(e3) {
  return `https://rollupjs.org/${e3}`;
}
const De = "troubleshooting/#error-name-is-not-exported-by-module", Le = "troubleshooting/#warning-sourcemap-is-likely-to-be-incorrect", Te = "configuration-options/#output-amd-id", Me = "configuration-options/#output-dir", Ve = "configuration-options/#output-exports", Be = "configuration-options/#output-extend", ze = "configuration-options/#output-format", Fe = "configuration-options/#output-experimentaldeepdynamicchunkoptimization", je = "configuration-options/#output-globals", Ue = "configuration-options/#output-inlinedynamicimports", Ge = "configuration-options/#output-interop", We = "configuration-options/#output-manualchunks", qe = "configuration-options/#output-name", He = "configuration-options/#output-sourcemapfile", Ke = "plugin-development/#this-getmoduleinfo";
function Ye(e3) {
  throw e3 instanceof Error || (e3 = Object.assign(new Error(e3.message), e3), Object.defineProperty(e3, "name", { value: "RollupError" })), e3;
}
function Xe(e3, t2, s2, i2) {
  if ("object" == typeof t2) {
    const { line: s3, column: n2 } = t2;
    e3.loc = { column: n2, file: i2, line: s3 };
  } else {
    e3.pos = t2;
    const { line: n2, column: r2 } = we(s2, t2, { offsetLine: 1 });
    e3.loc = { column: r2, file: i2, line: n2 };
  }
  if (void 0 === e3.frame) {
    const { line: t3, column: i3 } = e3.loc;
    e3.frame = _e(s2, t3, i3);
  }
}
const Qe = "ADDON_ERROR", Ze = "ALREADY_CLOSED", Je = "ANONYMOUS_PLUGIN_CACHE", et = "ASSET_NOT_FINALISED", tt = "CANNOT_EMIT_FROM_OPTIONS_HOOK", st = "CHUNK_NOT_GENERATED", it = "CIRCULAR_REEXPORT", nt = "DEPRECATED_FEATURE", rt = "DUPLICATE_PLUGIN_NAME", ot = "FILE_NAME_CONFLICT", at = "ILLEGAL_IDENTIFIER_AS_NAME", lt = "INVALID_CHUNK", ht = "INVALID_EXPORT_OPTION", ct = "INVALID_LOG_POSITION", ut = "INVALID_OPTION", dt = "INVALID_PLUGIN_HOOK", pt = "INVALID_ROLLUP_PHASE", ft = "INVALID_SETASSETSOURCE", mt = "MISSING_EXPORT", gt = "MISSING_GLOBAL_NAME", yt = "MISSING_IMPLICIT_DEPENDANT", xt = "MISSING_NAME_OPTION_FOR_IIFE_EXPORT", Et = "MISSING_NODE_BUILTINS", bt = "MISSING_OPTION", vt = "MIXED_EXPORTS", St = "NO_TRANSFORM_MAP_OR_AST_WITHOUT_CODE", At = "OPTIMIZE_CHUNK_STATUS", kt = "PLUGIN_ERROR", It = "SOURCEMAP_BROKEN", wt = "UNEXPECTED_NAMED_IMPORT", Pt = "UNKNOWN_OPTION", Ct = "UNRESOLVED_ENTRY", $t = "UNRESOLVED_IMPORT", Nt = "VALIDATION_ERROR";
function _t() {
  return { code: Ze, message: 'Bundle is already closed, no more calls to "generate" or "write" are allowed.' };
}
function Rt(e3) {
  return { code: "CANNOT_CALL_NAMESPACE", message: `Cannot call a namespace ("${e3}").` };
}
function Ot({ fileName: e3, code: t2 }, s2) {
  const i2 = { code: "CHUNK_INVALID", message: `Chunk "${e3}" is not valid JavaScript: ${s2.message}.` };
  return Xe(i2, s2.loc, t2, e3), i2;
}
function Dt(e3) {
  return { code: "CIRCULAR_DEPENDENCY", ids: e3, message: `Circular dependency: ${e3.map(T).join(" -> ")}` };
}
function Lt(e3, t2, { line: s2, column: i2 }) {
  return { code: "FIRST_SIDE_EFFECT", message: `First side effect in ${T(t2)} is at (${s2}:${i2})
${_e(e3, s2, i2)}` };
}
function Tt(e3, t2) {
  return { code: "ILLEGAL_REASSIGNMENT", message: `Illegal reassignment of import "${e3}" in "${T(t2)}".` };
}
function Mt(e3, t2, s2, i2) {
  return { code: "INCONSISTENT_IMPORT_ASSERTIONS", message: `Module "${T(i2)}" tried to import "${T(s2)}" with ${Vt(t2)} assertions, but it was already imported elsewhere with ${Vt(e3)} assertions. Please ensure that import assertions for the same module are always consistent.` };
}
const Vt = (e3) => {
  const t2 = Object.entries(e3);
  return 0 === t2.length ? "no" : t2.map(([e4, t3]) => `"${e4}": "${t3}"`).join(", ");
};
function Bt(e3, t2, s2) {
  return { code: ht, message: `"${e3}" was specified for "output.exports", but entry module "${T(s2)}" has the following exports: ${Re(t2)}`, url: Oe(Ve) };
}
function zt(e3, t2, s2, i2) {
  return { code: ut, message: `Invalid value ${void 0 === i2 ? "" : `${JSON.stringify(i2)} `}for option "${e3}" - ${s2}.`, url: Oe(t2) };
}
function Ft(e3, t2, s2) {
  const i2 = ".json" === C(s2);
  return { binding: e3, code: mt, exporter: s2, id: t2, message: `"${e3}" is not exported by "${T(s2)}", imported by "${T(t2)}".${i2 ? " (Note that you need @rollup/plugin-json to import JSON files)" : ""}`, url: Oe(De) };
}
function jt(e3) {
  const t2 = [...e3.implicitlyLoadedBefore].map((e4) => T(e4.id)).sort();
  return { code: yt, message: `Module "${T(e3.id)}" that should be implicitly loaded before ${Re(t2)} is not included in the module graph. Either it was not imported by an included module or only via a tree-shaken dynamic import, or no imported bindings were used and it had otherwise no side-effects.` };
}
function Ut(e3, t2, s2) {
  return { code: At, message: `${s2}, there are
${e3} chunks, of which
${t2} are below minChunkSize.` };
}
function Gt(e3, t2, { hook: s2, id: i2 } = {}) {
  const n2 = e3.code;
  return e3.pluginCode || null == n2 || "string" == typeof n2 && ("string" != typeof n2 || n2.startsWith("PLUGIN_")) || (e3.pluginCode = n2), e3.code = kt, e3.plugin = t2, s2 && (e3.hook = s2), i2 && (e3.id = i2), e3;
}
function Wt(e3) {
  return { code: It, message: `Multiple conflicting contents for sourcemap source ${e3}` };
}
function qt(e3, t2, s2) {
  const i2 = s2 ? "reexport" : "import";
  return { code: wt, exporter: e3, message: `The named export "${t2}" was ${i2}ed from the external module "${T(e3)}" even though its interop type is "defaultOnly". Either remove or change this ${i2} or change the value of the "output.interop" option.`, url: Oe(Ge) };
}
function Ht(e3) {
  return { code: wt, exporter: e3, message: `There was a namespace "*" reexport from the external module "${T(e3)}" even though its interop type is "defaultOnly". This will be ignored as namespace reexports only reexport named exports. If this is not intended, either remove or change this reexport or change the value of the "output.interop" option.`, url: Oe(Ge) };
}
function Kt(e3) {
  return { code: Nt, message: e3 };
}
function Yt(e3, t2, s2, i2, n2) {
  Xt(e3, t2, s2, i2.onLog, i2.strictDeprecations, n2);
}
function Xt(e3, t2, s2, i2, n2, r2) {
  if (s2 || n2) {
    const s3 = function(e4, t3, s4) {
      return { code: nt, message: e4, url: Oe(t3), ...s4 ? { plugin: s4 } : {} };
    }(e3, t2, r2);
    if (n2)
      return Ye(s3);
    i2(ve, s3);
  }
}
class Qt {
  constructor(e3, t2, s2, i2, n2, r2) {
    this.options = e3, this.id = t2, this.renormalizeRenderPath = n2, this.dynamicImporters = [], this.execIndex = 1 / 0, this.exportedVariables = /* @__PURE__ */ new Map(), this.importers = [], this.reexported = false, this.used = false, this.declarations = /* @__PURE__ */ new Map(), this.mostCommonSuggestion = 0, this.nameSuggestions = /* @__PURE__ */ new Map(), this.suggestedVariableName = be(t2.split(/[/\\]/).pop());
    const { importers: o2, dynamicImporters: a2 } = this, l2 = this.info = { assertions: r2, ast: null, code: null, dynamicallyImportedIdResolutions: me, dynamicallyImportedIds: me, get dynamicImporters() {
      return a2.sort();
    }, exportedBindings: null, exports: null, hasDefaultExport: null, get hasModuleSideEffects() {
      return Yt("Accessing ModuleInfo.hasModuleSideEffects from plugins is deprecated. Please use ModuleInfo.moduleSideEffects instead.", Ke, true, e3), l2.moduleSideEffects;
    }, id: t2, implicitlyLoadedAfterOneOf: me, implicitlyLoadedBefore: me, importedIdResolutions: me, importedIds: me, get importers() {
      return o2.sort();
    }, isEntry: false, isExternal: true, isIncluded: null, meta: i2, moduleSideEffects: s2, syntheticNamedExports: false };
    Object.defineProperty(this.info, "hasModuleSideEffects", { enumerable: false });
  }
  getVariableForExportName(e3) {
    const t2 = this.declarations.get(e3);
    if (t2)
      return [t2];
    const s2 = new de(this, e3);
    return this.declarations.set(e3, s2), this.exportedVariables.set(s2, e3), [s2];
  }
  suggestName(e3) {
    const t2 = (this.nameSuggestions.get(e3) ?? 0) + 1;
    this.nameSuggestions.set(e3, t2), t2 > this.mostCommonSuggestion && (this.mostCommonSuggestion = t2, this.suggestedVariableName = e3);
  }
  warnUnusedImports() {
    const e3 = [...this.declarations].filter(([e4, t3]) => "*" !== e4 && !t3.included && !this.reexported && !t3.referenced).map(([e4]) => e4);
    if (0 === e3.length)
      return;
    const t2 = /* @__PURE__ */ new Set();
    for (const s3 of e3)
      for (const e4 of this.declarations.get(s3).module.importers)
        t2.add(e4);
    const s2 = [...t2];
    var i2, n2, r2;
    this.options.onLog(ve, { code: "UNUSED_EXTERNAL_IMPORT", exporter: i2 = this.id, ids: r2 = s2, message: `${Re(n2 = e3, ["is", "are"])} imported from external module "${i2}" but never used in ${Re(r2.map((e4) => T(e4)))}.`, names: n2 });
  }
}
const Zt = { ArrayPattern(e3, t2) {
  for (const s2 of t2.elements)
    s2 && Zt[s2.type](e3, s2);
}, AssignmentPattern(e3, t2) {
  Zt[t2.left.type](e3, t2.left);
}, Identifier(e3, t2) {
  e3.push(t2.name);
}, MemberExpression() {
}, ObjectPattern(e3, t2) {
  for (const s2 of t2.properties)
    "RestElement" === s2.type ? Zt.RestElement(e3, s2) : Zt[s2.value.type](e3, s2.value);
}, RestElement(e3, t2) {
  Zt[t2.argument.type](e3, t2.argument);
} }, Jt = function(e3) {
  const t2 = [];
  return Zt[e3.type](t2, e3), t2;
};
function es() {
  return { brokenFlow: false, hasBreak: false, hasContinue: false, includedCallArguments: /* @__PURE__ */ new Set(), includedLabels: /* @__PURE__ */ new Set() };
}
function ts() {
  return { accessed: new J(), assigned: new J(), brokenFlow: false, called: new te(), hasBreak: false, hasContinue: false, ignore: { breaks: false, continues: false, labels: /* @__PURE__ */ new Set(), returnYield: false, this: false }, includedLabels: /* @__PURE__ */ new Set(), instantiated: new te(), replacedVariableInits: /* @__PURE__ */ new Map() };
}
function ss(e3, t2 = null) {
  return Object.create(t2, e3);
}
new Set("break case class catch const continue debugger default delete do else export extends finally for function if import in instanceof let new return super switch this throw try typeof var void while with yield enum await implements package protected static interface private public arguments Infinity NaN undefined null true false eval uneval isFinite isNaN parseFloat parseInt decodeURI decodeURIComponent encodeURI encodeURIComponent escape unescape Object Function Boolean Symbol Error EvalError InternalError RangeError ReferenceError SyntaxError TypeError URIError Number Math Date String RegExp Array Int8Array Uint8Array Uint8ClampedArray Int16Array Uint16Array Int32Array Uint32Array Float32Array Float64Array Map Set WeakMap WeakSet SIMD ArrayBuffer DataView JSON Promise Generator GeneratorFunction Reflect Proxy Intl".split(" ")).add("");
const is = new class extends ne {
  getLiteralValueAtPath() {
  }
}(), ns = { value: { hasEffectsWhenCalled: null, returns: re } }, rs = new class extends ne {
  getReturnExpressionWhenCalledAtPath(e3) {
    return 1 === e3.length ? xs(ps, e3[0]) : oe;
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return 0 === t2.type ? e3.length > 1 : 2 !== t2.type || 1 !== e3.length || ys(ps, e3[0], t2, s2);
  }
}(), os = { value: { hasEffectsWhenCalled: null, returns: rs } }, as = new class extends ne {
  getReturnExpressionWhenCalledAtPath(e3) {
    return 1 === e3.length ? xs(fs, e3[0]) : oe;
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return 0 === t2.type ? e3.length > 1 : 2 !== t2.type || 1 !== e3.length || ys(fs, e3[0], t2, s2);
  }
}(), ls = { value: { hasEffectsWhenCalled: null, returns: as } }, hs = new class extends ne {
  getReturnExpressionWhenCalledAtPath(e3) {
    return 1 === e3.length ? xs(gs, e3[0]) : oe;
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return 0 === t2.type ? e3.length > 1 : 2 !== t2.type || 1 !== e3.length || ys(gs, e3[0], t2, s2);
  }
}(), cs = { value: { hasEffectsWhenCalled: null, returns: hs } }, us = { value: { hasEffectsWhenCalled({ args: e3 }, t2) {
  const s2 = e3[2];
  return e3.length < 3 || "symbol" == typeof s2.getLiteralValueAtPath(K, ee, { deoptimizeCache() {
  } }) && s2.hasEffectsOnInteractionAtPath(K, ce, t2);
}, returns: hs } }, ds = ss({ hasOwnProperty: os, isPrototypeOf: os, propertyIsEnumerable: os, toLocaleString: cs, toString: cs, valueOf: ns }), ps = ss({ valueOf: os }, ds), fs = ss({ toExponential: cs, toFixed: cs, toLocaleString: cs, toPrecision: cs, valueOf: ls }, ds), ms = ss({ exec: ns, test: os }, ds), gs = ss({ anchor: cs, at: ns, big: cs, blink: cs, bold: cs, charAt: cs, charCodeAt: ls, codePointAt: ns, concat: cs, endsWith: os, fixed: cs, fontcolor: cs, fontsize: cs, includes: os, indexOf: ls, italics: cs, lastIndexOf: ls, link: cs, localeCompare: ls, match: ns, matchAll: ns, normalize: cs, padEnd: cs, padStart: cs, repeat: cs, replace: us, replaceAll: us, search: ls, slice: cs, small: cs, split: ns, startsWith: os, strike: cs, sub: cs, substr: cs, substring: cs, sup: cs, toLocaleLowerCase: cs, toLocaleUpperCase: cs, toLowerCase: cs, toString: cs, toUpperCase: cs, trim: cs, trimEnd: cs, trimLeft: cs, trimRight: cs, trimStart: cs, valueOf: cs }, ds);
function ys(e3, t2, s2, i2) {
  var _a2, _b;
  return "string" != typeof t2 || !e3[t2] || (((_b = (_a2 = e3[t2]).hasEffectsWhenCalled) == null ? void 0 : _b.call(_a2, s2, i2)) || false);
}
function xs(e3, t2) {
  return "string" == typeof t2 && e3[t2] ? [e3[t2].returns, false] : oe;
}
function Es(e3, t2, s2) {
  s2(e3, t2);
}
function bs(e3, t2, s2) {
}
var vs = {};
vs.Program = vs.BlockStatement = vs.StaticBlock = function(e3, t2, s2) {
  for (var i2 = 0, n2 = e3.body; i2 < n2.length; i2 += 1) {
    s2(n2[i2], t2, "Statement");
  }
}, vs.Statement = Es, vs.EmptyStatement = bs, vs.ExpressionStatement = vs.ParenthesizedExpression = vs.ChainExpression = function(e3, t2, s2) {
  return s2(e3.expression, t2, "Expression");
}, vs.IfStatement = function(e3, t2, s2) {
  s2(e3.test, t2, "Expression"), s2(e3.consequent, t2, "Statement"), e3.alternate && s2(e3.alternate, t2, "Statement");
}, vs.LabeledStatement = function(e3, t2, s2) {
  return s2(e3.body, t2, "Statement");
}, vs.BreakStatement = vs.ContinueStatement = bs, vs.WithStatement = function(e3, t2, s2) {
  s2(e3.object, t2, "Expression"), s2(e3.body, t2, "Statement");
}, vs.SwitchStatement = function(e3, t2, s2) {
  s2(e3.discriminant, t2, "Expression");
  for (var i2 = 0, n2 = e3.cases; i2 < n2.length; i2 += 1) {
    var r2 = n2[i2];
    r2.test && s2(r2.test, t2, "Expression");
    for (var o2 = 0, a2 = r2.consequent; o2 < a2.length; o2 += 1) {
      s2(a2[o2], t2, "Statement");
    }
  }
}, vs.SwitchCase = function(e3, t2, s2) {
  e3.test && s2(e3.test, t2, "Expression");
  for (var i2 = 0, n2 = e3.consequent; i2 < n2.length; i2 += 1) {
    s2(n2[i2], t2, "Statement");
  }
}, vs.ReturnStatement = vs.YieldExpression = vs.AwaitExpression = function(e3, t2, s2) {
  e3.argument && s2(e3.argument, t2, "Expression");
}, vs.ThrowStatement = vs.SpreadElement = function(e3, t2, s2) {
  return s2(e3.argument, t2, "Expression");
}, vs.TryStatement = function(e3, t2, s2) {
  s2(e3.block, t2, "Statement"), e3.handler && s2(e3.handler, t2), e3.finalizer && s2(e3.finalizer, t2, "Statement");
}, vs.CatchClause = function(e3, t2, s2) {
  e3.param && s2(e3.param, t2, "Pattern"), s2(e3.body, t2, "Statement");
}, vs.WhileStatement = vs.DoWhileStatement = function(e3, t2, s2) {
  s2(e3.test, t2, "Expression"), s2(e3.body, t2, "Statement");
}, vs.ForStatement = function(e3, t2, s2) {
  e3.init && s2(e3.init, t2, "ForInit"), e3.test && s2(e3.test, t2, "Expression"), e3.update && s2(e3.update, t2, "Expression"), s2(e3.body, t2, "Statement");
}, vs.ForInStatement = vs.ForOfStatement = function(e3, t2, s2) {
  s2(e3.left, t2, "ForInit"), s2(e3.right, t2, "Expression"), s2(e3.body, t2, "Statement");
}, vs.ForInit = function(e3, t2, s2) {
  "VariableDeclaration" === e3.type ? s2(e3, t2) : s2(e3, t2, "Expression");
}, vs.DebuggerStatement = bs, vs.FunctionDeclaration = function(e3, t2, s2) {
  return s2(e3, t2, "Function");
}, vs.VariableDeclaration = function(e3, t2, s2) {
  for (var i2 = 0, n2 = e3.declarations; i2 < n2.length; i2 += 1) {
    s2(n2[i2], t2);
  }
}, vs.VariableDeclarator = function(e3, t2, s2) {
  s2(e3.id, t2, "Pattern"), e3.init && s2(e3.init, t2, "Expression");
}, vs.Function = function(e3, t2, s2) {
  e3.id && s2(e3.id, t2, "Pattern");
  for (var i2 = 0, n2 = e3.params; i2 < n2.length; i2 += 1) {
    s2(n2[i2], t2, "Pattern");
  }
  s2(e3.body, t2, e3.expression ? "Expression" : "Statement");
}, vs.Pattern = function(e3, t2, s2) {
  "Identifier" === e3.type ? s2(e3, t2, "VariablePattern") : "MemberExpression" === e3.type ? s2(e3, t2, "MemberPattern") : s2(e3, t2);
}, vs.VariablePattern = bs, vs.MemberPattern = Es, vs.RestElement = function(e3, t2, s2) {
  return s2(e3.argument, t2, "Pattern");
}, vs.ArrayPattern = function(e3, t2, s2) {
  for (var i2 = 0, n2 = e3.elements; i2 < n2.length; i2 += 1) {
    var r2 = n2[i2];
    r2 && s2(r2, t2, "Pattern");
  }
}, vs.ObjectPattern = function(e3, t2, s2) {
  for (var i2 = 0, n2 = e3.properties; i2 < n2.length; i2 += 1) {
    var r2 = n2[i2];
    "Property" === r2.type ? (r2.computed && s2(r2.key, t2, "Expression"), s2(r2.value, t2, "Pattern")) : "RestElement" === r2.type && s2(r2.argument, t2, "Pattern");
  }
}, vs.Expression = Es, vs.ThisExpression = vs.Super = vs.MetaProperty = bs, vs.ArrayExpression = function(e3, t2, s2) {
  for (var i2 = 0, n2 = e3.elements; i2 < n2.length; i2 += 1) {
    var r2 = n2[i2];
    r2 && s2(r2, t2, "Expression");
  }
}, vs.ObjectExpression = function(e3, t2, s2) {
  for (var i2 = 0, n2 = e3.properties; i2 < n2.length; i2 += 1) {
    s2(n2[i2], t2);
  }
}, vs.FunctionExpression = vs.ArrowFunctionExpression = vs.FunctionDeclaration, vs.SequenceExpression = function(e3, t2, s2) {
  for (var i2 = 0, n2 = e3.expressions; i2 < n2.length; i2 += 1) {
    s2(n2[i2], t2, "Expression");
  }
}, vs.TemplateLiteral = function(e3, t2, s2) {
  for (var i2 = 0, n2 = e3.quasis; i2 < n2.length; i2 += 1) {
    s2(n2[i2], t2);
  }
  for (var r2 = 0, o2 = e3.expressions; r2 < o2.length; r2 += 1) {
    s2(o2[r2], t2, "Expression");
  }
}, vs.TemplateElement = bs, vs.UnaryExpression = vs.UpdateExpression = function(e3, t2, s2) {
  s2(e3.argument, t2, "Expression");
}, vs.BinaryExpression = vs.LogicalExpression = function(e3, t2, s2) {
  s2(e3.left, t2, "Expression"), s2(e3.right, t2, "Expression");
}, vs.AssignmentExpression = vs.AssignmentPattern = function(e3, t2, s2) {
  s2(e3.left, t2, "Pattern"), s2(e3.right, t2, "Expression");
}, vs.ConditionalExpression = function(e3, t2, s2) {
  s2(e3.test, t2, "Expression"), s2(e3.consequent, t2, "Expression"), s2(e3.alternate, t2, "Expression");
}, vs.NewExpression = vs.CallExpression = function(e3, t2, s2) {
  if (s2(e3.callee, t2, "Expression"), e3.arguments)
    for (var i2 = 0, n2 = e3.arguments; i2 < n2.length; i2 += 1) {
      s2(n2[i2], t2, "Expression");
    }
}, vs.MemberExpression = function(e3, t2, s2) {
  s2(e3.object, t2, "Expression"), e3.computed && s2(e3.property, t2, "Expression");
}, vs.ExportNamedDeclaration = vs.ExportDefaultDeclaration = function(e3, t2, s2) {
  e3.declaration && s2(e3.declaration, t2, "ExportNamedDeclaration" === e3.type || e3.declaration.id ? "Statement" : "Expression"), e3.source && s2(e3.source, t2, "Expression");
}, vs.ExportAllDeclaration = function(e3, t2, s2) {
  e3.exported && s2(e3.exported, t2), s2(e3.source, t2, "Expression");
}, vs.ImportDeclaration = function(e3, t2, s2) {
  for (var i2 = 0, n2 = e3.specifiers; i2 < n2.length; i2 += 1) {
    s2(n2[i2], t2);
  }
  s2(e3.source, t2, "Expression");
}, vs.ImportExpression = function(e3, t2, s2) {
  s2(e3.source, t2, "Expression");
}, vs.ImportSpecifier = vs.ImportDefaultSpecifier = vs.ImportNamespaceSpecifier = vs.Identifier = vs.PrivateIdentifier = vs.Literal = bs, vs.TaggedTemplateExpression = function(e3, t2, s2) {
  s2(e3.tag, t2, "Expression"), s2(e3.quasi, t2, "Expression");
}, vs.ClassDeclaration = vs.ClassExpression = function(e3, t2, s2) {
  return s2(e3, t2, "Class");
}, vs.Class = function(e3, t2, s2) {
  e3.id && s2(e3.id, t2, "Pattern"), e3.superClass && s2(e3.superClass, t2, "Expression"), s2(e3.body, t2);
}, vs.ClassBody = function(e3, t2, s2) {
  for (var i2 = 0, n2 = e3.body; i2 < n2.length; i2 += 1) {
    s2(n2[i2], t2);
  }
}, vs.MethodDefinition = vs.PropertyDefinition = vs.Property = function(e3, t2, s2) {
  e3.computed && s2(e3.key, t2, "Expression"), e3.value && s2(e3.value, t2, "Expression");
};
const Ss = "ArrowFunctionExpression", As = "BinaryExpression", ks = "BlockStatement", Is = "CallExpression", ws = "ChainExpression", Ps = "ConditionalExpression", Cs = "ExportDefaultDeclaration", $s = "ExportNamedDeclaration", Ns = "ExpressionStatement", _s = "FunctionDeclaration", Rs = "Identifier", Os = "LogicalExpression", Ds = "NewExpression", Ls = "Program", Ts = "SequenceExpression", Ms = "VariableDeclarator", Vs = "VariableDeclaration";
let Bs = "sourceMa";
Bs += "ppingURL";
const zs = new RegExp(`^#[ \\f\\r\\t\\v\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff]+${Bs}=.+`), Fs = "_rollupAnnotations", js = "_rollupRemoved";
function Us(e3, t2, s2 = e3.type) {
  const { annotations: i2, code: n2 } = t2;
  let r2 = i2[t2.annotationIndex];
  for (; r2 && e3.start >= r2.end; )
    qs(e3, r2, n2), r2 = i2[++t2.annotationIndex];
  if (r2 && r2.end <= e3.end)
    for (vs[s2](e3, t2, Us); (r2 = i2[t2.annotationIndex]) && r2.end <= e3.end; )
      ++t2.annotationIndex, Ys(e3, r2, false);
}
const Gs = /[^\s(]/g, Ws = /\S/g;
function qs(e3, t2, s2) {
  const i2 = [];
  let n2;
  if (Hs(s2.slice(t2.end, e3.start), Gs)) {
    const t3 = e3.start;
    for (; ; ) {
      switch (i2.push(e3), e3.type) {
        case Ns:
        case ws:
          e3 = e3.expression;
          continue;
        case Ts:
          if (Hs(s2.slice(t3, e3.start), Ws)) {
            e3 = e3.expressions[0];
            continue;
          }
          n2 = true;
          break;
        case Ps:
          if (Hs(s2.slice(t3, e3.start), Ws)) {
            e3 = e3.test;
            continue;
          }
          n2 = true;
          break;
        case Os:
        case As:
          if (Hs(s2.slice(t3, e3.start), Ws)) {
            e3 = e3.left;
            continue;
          }
          n2 = true;
          break;
        case $s:
        case Cs:
          e3 = e3.declaration;
          continue;
        case Vs: {
          const t4 = e3;
          if ("const" === t4.kind) {
            e3 = t4.declarations[0].init;
            continue;
          }
          n2 = true;
          break;
        }
        case Ms:
          e3 = e3.init;
          continue;
        case _s:
        case Ss:
        case Is:
        case Ds:
          break;
        default:
          n2 = true;
      }
      break;
    }
  } else
    n2 = true;
  if (n2)
    Ys(e3, t2, false);
  else
    for (const e4 of i2)
      Ys(e4, t2, true);
}
function Hs(e3, t2) {
  let s2;
  for (; null !== (s2 = t2.exec(e3)); ) {
    if ("/" === s2[0]) {
      const s3 = e3.charCodeAt(t2.lastIndex);
      if (42 === s3) {
        t2.lastIndex = e3.indexOf("*/", t2.lastIndex + 1) + 2;
        continue;
      }
      if (47 === s3) {
        t2.lastIndex = e3.indexOf("\n", t2.lastIndex + 1) + 1;
        continue;
      }
    }
    return t2.lastIndex = 0, false;
  }
  return true;
}
const Ks = [["pure", /[#@]__PURE__/], ["noSideEffects", /[#@]__NO_SIDE_EFFECTS__/]];
function Ys(e3, t2, s2) {
  const i2 = s2 ? Fs : js, n2 = e3[i2];
  n2 ? n2.push(t2) : e3[i2] = [t2];
}
const Xs = { ImportExpression: ["arguments"], Literal: [], Program: ["body"] };
const Qs = "variables";
class Zs extends ne {
  constructor(e3, t2, s2, i2 = false) {
    super(), this.deoptimized = false, this.esTreeNode = i2 ? e3 : null, this.keys = Xs[e3.type] || function(e4) {
      return Xs[e4.type] = Object.keys(e4).filter((t3) => "object" == typeof e4[t3] && 95 !== t3.charCodeAt(0)), Xs[e4.type];
    }(e3), this.parent = t2, this.context = t2.context, this.createScope(s2), this.parseNode(e3), this.initialise(), this.context.magicString.addSourcemapLocation(this.start), this.context.magicString.addSourcemapLocation(this.end);
  }
  addExportedVariables(e3, t2) {
  }
  bind() {
    for (const e3 of this.keys) {
      const t2 = this[e3];
      if (Array.isArray(t2))
        for (const e4 of t2)
          e4 == null ? void 0 : e4.bind();
      else
        t2 && t2.bind();
    }
  }
  createScope(e3) {
    this.scope = e3;
  }
  hasEffects(e3) {
    this.deoptimized || this.applyDeoptimizations();
    for (const t2 of this.keys) {
      const s2 = this[t2];
      if (null !== s2) {
        if (Array.isArray(s2)) {
          for (const t3 of s2)
            if (t3 == null ? void 0 : t3.hasEffects(e3))
              return true;
        } else if (s2.hasEffects(e3))
          return true;
      }
    }
    return false;
  }
  hasEffectsAsAssignmentTarget(e3, t2) {
    return this.hasEffects(e3) || this.hasEffectsOnInteractionAtPath(K, this.assignmentInteraction, e3);
  }
  include(e3, t2, s2) {
    this.deoptimized || this.applyDeoptimizations(), this.included = true;
    for (const s3 of this.keys) {
      const i2 = this[s3];
      if (null !== i2)
        if (Array.isArray(i2))
          for (const s4 of i2)
            s4 == null ? void 0 : s4.include(e3, t2);
        else
          i2.include(e3, t2);
    }
  }
  includeAsAssignmentTarget(e3, t2, s2) {
    this.include(e3, t2);
  }
  initialise() {
  }
  insertSemicolon(e3) {
    ";" !== e3.original[this.end - 1] && e3.appendLeft(this.end, ";");
  }
  parseNode(e3, t2) {
    for (const [s2, i2] of Object.entries(e3))
      if (!this.hasOwnProperty(s2))
        if (95 === s2.charCodeAt(0)) {
          if (s2 === Fs) {
            const e4 = i2;
            this.annotations = e4, this.context.options.treeshake.annotations && (this.annotationNoSideEffects = e4.some((e5) => "noSideEffects" === e5.annotationType), this.annotationPure = e4.some((e5) => "pure" === e5.annotationType));
          } else if (s2 === js)
            for (const { start: e4, end: t3 } of i2)
              this.context.magicString.remove(e4, t3);
        } else if ("object" != typeof i2 || null === i2)
          this[s2] = i2;
        else if (Array.isArray(i2)) {
          this[s2] = [];
          for (const e4 of i2)
            this[s2].push(null === e4 ? null : new (this.context.getNodeConstructor(e4.type))(e4, this, this.scope, t2 == null ? void 0 : t2.includes(s2)));
        } else
          this[s2] = new (this.context.getNodeConstructor(i2.type))(i2, this, this.scope, t2 == null ? void 0 : t2.includes(s2));
  }
  render(e3, t2) {
    for (const s2 of this.keys) {
      const i2 = this[s2];
      if (null !== i2)
        if (Array.isArray(i2))
          for (const s3 of i2)
            s3 == null ? void 0 : s3.render(e3, t2);
        else
          i2.render(e3, t2);
    }
  }
  setAssignedValue(e3) {
    this.assignmentInteraction = { args: [null, e3], type: 1 };
  }
  shouldBeIncluded(e3) {
    return this.included || !e3.brokenFlow && this.hasEffects(ts());
  }
  applyDeoptimizations() {
    this.deoptimized = true;
    for (const e3 of this.keys) {
      const t2 = this[e3];
      if (null !== t2)
        if (Array.isArray(t2))
          for (const e4 of t2)
            e4 == null ? void 0 : e4.deoptimizePath(Y);
        else
          t2.deoptimizePath(Y);
    }
    this.context.requestTreeshakingPass();
  }
}
class Js extends Zs {
  deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2) {
    t2.length > 0 && this.argument.deoptimizeArgumentsOnInteractionAtPath(e3, [G, ...t2], s2);
  }
  hasEffects(e3) {
    this.deoptimized || this.applyDeoptimizations();
    const { propertyReadSideEffects: t2 } = this.context.options.treeshake;
    return this.argument.hasEffects(e3) || t2 && ("always" === t2 || this.argument.hasEffectsOnInteractionAtPath(Y, le, e3));
  }
  applyDeoptimizations() {
    this.deoptimized = true, this.argument.deoptimizePath([G, G]), this.context.requestTreeshakingPass();
  }
}
class ei extends ne {
  constructor(e3) {
    super(), this.description = e3;
  }
  deoptimizeArgumentsOnInteractionAtPath({ args: e3, type: t2 }, s2) {
    var _a2;
    2 === t2 && 0 === s2.length && this.description.mutatesSelfAsArray && ((_a2 = e3[0]) == null ? void 0 : _a2.deoptimizePath(Q));
  }
  getReturnExpressionWhenCalledAtPath(e3, { args: t2 }) {
    return e3.length > 0 ? oe : [this.description.returnsPrimitive || ("self" === this.description.returns ? t2[0] || re : this.description.returns()), false];
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    var _a2, _b;
    const { type: i2 } = t2;
    if (e3.length > (0 === i2 ? 1 : 0))
      return true;
    if (2 === i2) {
      const { args: e4 } = t2;
      if (true === this.description.mutatesSelfAsArray && ((_a2 = e4[0]) == null ? void 0 : _a2.hasEffectsOnInteractionAtPath(Q, he, s2)))
        return true;
      if (this.description.callsArgs) {
        for (const t3 of this.description.callsArgs)
          if ((_b = e4[t3 + 1]) == null ? void 0 : _b.hasEffectsOnInteractionAtPath(K, ce, s2))
            return true;
      }
    }
    return false;
  }
}
const ti = [new ei({ callsArgs: null, mutatesSelfAsArray: false, returns: null, returnsPrimitive: rs })], si = [new ei({ callsArgs: null, mutatesSelfAsArray: false, returns: null, returnsPrimitive: hs })], ii = [new ei({ callsArgs: null, mutatesSelfAsArray: false, returns: null, returnsPrimitive: as })], ni = [new ei({ callsArgs: null, mutatesSelfAsArray: false, returns: null, returnsPrimitive: re })], ri = /^\d+$/;
class oi extends ne {
  constructor(e3, t2, s2 = false) {
    if (super(), this.prototypeExpression = t2, this.immutable = s2, this.additionalExpressionsToBeDeoptimized = /* @__PURE__ */ new Set(), this.allProperties = [], this.deoptimizedPaths = /* @__PURE__ */ Object.create(null), this.expressionsToBeDeoptimizedByKey = /* @__PURE__ */ Object.create(null), this.gettersByKey = /* @__PURE__ */ Object.create(null), this.hasLostTrack = false, this.hasUnknownDeoptimizedInteger = false, this.hasUnknownDeoptimizedProperty = false, this.propertiesAndGettersByKey = /* @__PURE__ */ Object.create(null), this.propertiesAndSettersByKey = /* @__PURE__ */ Object.create(null), this.settersByKey = /* @__PURE__ */ Object.create(null), this.unknownIntegerProps = [], this.unmatchableGetters = [], this.unmatchablePropertiesAndGetters = [], this.unmatchableSetters = [], Array.isArray(e3))
      this.buildPropertyMaps(e3);
    else {
      this.propertiesAndGettersByKey = this.propertiesAndSettersByKey = e3;
      for (const t3 of Object.values(e3))
        this.allProperties.push(...t3);
    }
  }
  deoptimizeAllProperties(e3) {
    var _a2;
    const t2 = this.hasLostTrack || this.hasUnknownDeoptimizedProperty;
    if (e3 ? this.hasUnknownDeoptimizedProperty = true : this.hasLostTrack = true, !t2) {
      for (const e4 of [...Object.values(this.propertiesAndGettersByKey), ...Object.values(this.settersByKey)])
        for (const t3 of e4)
          t3.deoptimizePath(Y);
      (_a2 = this.prototypeExpression) == null ? void 0 : _a2.deoptimizePath([G, G]), this.deoptimizeCachedEntities();
    }
  }
  deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2) {
    var _a2;
    const [i2, ...n2] = t2, { args: r2, type: o2 } = e3;
    if (this.hasLostTrack || (2 === o2 || t2.length > 1) && (this.hasUnknownDeoptimizedProperty || "string" == typeof i2 && this.deoptimizedPaths[i2]))
      return void ae(e3);
    const [a2, l2, h2] = 2 === o2 || t2.length > 1 ? [this.propertiesAndGettersByKey, this.propertiesAndGettersByKey, this.unmatchablePropertiesAndGetters] : 0 === o2 ? [this.propertiesAndGettersByKey, this.gettersByKey, this.unmatchableGetters] : [this.propertiesAndSettersByKey, this.settersByKey, this.unmatchableSetters];
    if ("string" == typeof i2) {
      if (a2[i2]) {
        const t3 = l2[i2];
        if (t3)
          for (const i3 of t3)
            i3.deoptimizeArgumentsOnInteractionAtPath(e3, n2, s2);
        if (!this.immutable)
          for (const e4 of r2)
            e4 && this.additionalExpressionsToBeDeoptimized.add(e4);
        return;
      }
      for (const t3 of h2)
        t3.deoptimizeArgumentsOnInteractionAtPath(e3, n2, s2);
      if (ri.test(i2))
        for (const t3 of this.unknownIntegerProps)
          t3.deoptimizeArgumentsOnInteractionAtPath(e3, n2, s2);
    } else {
      for (const t3 of [...Object.values(l2), h2])
        for (const i3 of t3)
          i3.deoptimizeArgumentsOnInteractionAtPath(e3, n2, s2);
      for (const t3 of this.unknownIntegerProps)
        t3.deoptimizeArgumentsOnInteractionAtPath(e3, n2, s2);
    }
    if (!this.immutable)
      for (const e4 of r2)
        e4 && this.additionalExpressionsToBeDeoptimized.add(e4);
    (_a2 = this.prototypeExpression) == null ? void 0 : _a2.deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2);
  }
  deoptimizeIntegerProperties() {
    if (!(this.hasLostTrack || this.hasUnknownDeoptimizedProperty || this.hasUnknownDeoptimizedInteger)) {
      this.hasUnknownDeoptimizedInteger = true;
      for (const [e3, t2] of Object.entries(this.propertiesAndGettersByKey))
        if (ri.test(e3))
          for (const e4 of t2)
            e4.deoptimizePath(Y);
      this.deoptimizeCachedIntegerEntities();
    }
  }
  deoptimizePath(e3) {
    var _a2;
    if (this.hasLostTrack || this.immutable)
      return;
    const t2 = e3[0];
    if (1 === e3.length) {
      if ("string" != typeof t2)
        return t2 === q ? this.deoptimizeIntegerProperties() : this.deoptimizeAllProperties(t2 === W);
      if (!this.deoptimizedPaths[t2]) {
        this.deoptimizedPaths[t2] = true;
        const e4 = this.expressionsToBeDeoptimizedByKey[t2];
        if (e4)
          for (const t3 of e4)
            t3.deoptimizeCache();
      }
    }
    const s2 = 1 === e3.length ? Y : e3.slice(1);
    for (const e4 of "string" == typeof t2 ? [...this.propertiesAndGettersByKey[t2] || this.unmatchablePropertiesAndGetters, ...this.settersByKey[t2] || this.unmatchableSetters] : this.allProperties)
      e4.deoptimizePath(s2);
    (_a2 = this.prototypeExpression) == null ? void 0 : _a2.deoptimizePath(1 === e3.length ? [...e3, G] : e3);
  }
  getLiteralValueAtPath(e3, t2, s2) {
    if (0 === e3.length)
      return ie;
    const i2 = e3[0], n2 = this.getMemberExpressionAndTrackDeopt(i2, s2);
    return n2 ? n2.getLiteralValueAtPath(e3.slice(1), t2, s2) : this.prototypeExpression ? this.prototypeExpression.getLiteralValueAtPath(e3, t2, s2) : 1 !== e3.length ? se : void 0;
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    if (0 === e3.length)
      return oe;
    const [n2, ...r2] = e3, o2 = this.getMemberExpressionAndTrackDeopt(n2, i2);
    return o2 ? o2.getReturnExpressionWhenCalledAtPath(r2, t2, s2, i2) : this.prototypeExpression ? this.prototypeExpression.getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) : oe;
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    const [i2, ...n2] = e3;
    if (n2.length > 0 || 2 === t2.type) {
      const r3 = this.getMemberExpression(i2);
      return r3 ? r3.hasEffectsOnInteractionAtPath(n2, t2, s2) : !this.prototypeExpression || this.prototypeExpression.hasEffectsOnInteractionAtPath(e3, t2, s2);
    }
    if (i2 === W)
      return false;
    if (this.hasLostTrack)
      return true;
    const [r2, o2, a2] = 0 === t2.type ? [this.propertiesAndGettersByKey, this.gettersByKey, this.unmatchableGetters] : [this.propertiesAndSettersByKey, this.settersByKey, this.unmatchableSetters];
    if ("string" == typeof i2) {
      if (r2[i2]) {
        const e4 = o2[i2];
        if (e4) {
          for (const i3 of e4)
            if (i3.hasEffectsOnInteractionAtPath(n2, t2, s2))
              return true;
        }
        return false;
      }
      for (const e4 of a2)
        if (e4.hasEffectsOnInteractionAtPath(n2, t2, s2))
          return true;
    } else
      for (const e4 of [...Object.values(o2), a2])
        for (const i3 of e4)
          if (i3.hasEffectsOnInteractionAtPath(n2, t2, s2))
            return true;
    return !!this.prototypeExpression && this.prototypeExpression.hasEffectsOnInteractionAtPath(e3, t2, s2);
  }
  buildPropertyMaps(e3) {
    const { allProperties: t2, propertiesAndGettersByKey: s2, propertiesAndSettersByKey: i2, settersByKey: n2, gettersByKey: r2, unknownIntegerProps: o2, unmatchablePropertiesAndGetters: a2, unmatchableGetters: l2, unmatchableSetters: h2 } = this, c2 = [];
    for (let u2 = e3.length - 1; u2 >= 0; u2--) {
      const { key: d2, kind: p2, property: f2 } = e3[u2];
      if (t2.push(f2), "string" == typeof d2)
        "set" === p2 ? i2[d2] || (i2[d2] = [f2, ...c2], n2[d2] = [f2, ...h2]) : "get" === p2 ? s2[d2] || (s2[d2] = [f2, ...a2], r2[d2] = [f2, ...l2]) : (i2[d2] || (i2[d2] = [f2, ...c2]), s2[d2] || (s2[d2] = [f2, ...a2]));
      else {
        if (d2 === q) {
          o2.push(f2);
          continue;
        }
        "set" === p2 && h2.push(f2), "get" === p2 && l2.push(f2), "get" !== p2 && c2.push(f2), "set" !== p2 && a2.push(f2);
      }
    }
  }
  deoptimizeCachedEntities() {
    for (const e3 of Object.values(this.expressionsToBeDeoptimizedByKey))
      for (const t2 of e3)
        t2.deoptimizeCache();
    for (const e3 of this.additionalExpressionsToBeDeoptimized)
      e3.deoptimizePath(Y);
  }
  deoptimizeCachedIntegerEntities() {
    for (const [e3, t2] of Object.entries(this.expressionsToBeDeoptimizedByKey))
      if (ri.test(e3))
        for (const e4 of t2)
          e4.deoptimizeCache();
    for (const e3 of this.additionalExpressionsToBeDeoptimized)
      e3.deoptimizePath(Q);
  }
  getMemberExpression(e3) {
    if (this.hasLostTrack || this.hasUnknownDeoptimizedProperty || "string" != typeof e3 || this.hasUnknownDeoptimizedInteger && ri.test(e3) || this.deoptimizedPaths[e3])
      return re;
    const t2 = this.propertiesAndGettersByKey[e3];
    return 1 === (t2 == null ? void 0 : t2.length) ? t2[0] : t2 || this.unmatchablePropertiesAndGetters.length > 0 || this.unknownIntegerProps.length > 0 && ri.test(e3) ? re : null;
  }
  getMemberExpressionAndTrackDeopt(e3, t2) {
    if ("string" != typeof e3)
      return re;
    const s2 = this.getMemberExpression(e3);
    if (s2 !== re && !this.immutable) {
      (this.expressionsToBeDeoptimizedByKey[e3] = this.expressionsToBeDeoptimizedByKey[e3] || []).push(t2);
    }
    return s2;
  }
}
const ai = (e3) => "string" == typeof e3 && /^\d+$/.test(e3), li = new class extends ne {
  deoptimizeArgumentsOnInteractionAtPath(e3, t2) {
    2 !== e3.type || 1 !== t2.length || ai(t2[0]) || ae(e3);
  }
  getLiteralValueAtPath(e3) {
    return 1 === e3.length && ai(e3[0]) ? void 0 : se;
  }
  hasEffectsOnInteractionAtPath(e3, { type: t2 }) {
    return e3.length > 1 || 2 === t2;
  }
}(), hi = new oi({ __proto__: null, hasOwnProperty: ti, isPrototypeOf: ti, propertyIsEnumerable: ti, toLocaleString: si, toString: si, valueOf: ni }, li, true), ci = [{ key: q, kind: "init", property: re }, { key: "length", kind: "init", property: as }], ui = [new ei({ callsArgs: [0], mutatesSelfAsArray: "deopt-only", returns: null, returnsPrimitive: rs })], di = [new ei({ callsArgs: [0], mutatesSelfAsArray: "deopt-only", returns: null, returnsPrimitive: as })], pi = [new ei({ callsArgs: null, mutatesSelfAsArray: true, returns: () => new oi(ci, Si), returnsPrimitive: null })], fi = [new ei({ callsArgs: null, mutatesSelfAsArray: "deopt-only", returns: () => new oi(ci, Si), returnsPrimitive: null })], mi = [new ei({ callsArgs: [0], mutatesSelfAsArray: "deopt-only", returns: () => new oi(ci, Si), returnsPrimitive: null })], gi = [new ei({ callsArgs: null, mutatesSelfAsArray: true, returns: null, returnsPrimitive: as })], yi = [new ei({ callsArgs: null, mutatesSelfAsArray: true, returns: null, returnsPrimitive: re })], xi = [new ei({ callsArgs: null, mutatesSelfAsArray: "deopt-only", returns: null, returnsPrimitive: re })], Ei = [new ei({ callsArgs: [0], mutatesSelfAsArray: "deopt-only", returns: null, returnsPrimitive: re })], bi = [new ei({ callsArgs: null, mutatesSelfAsArray: true, returns: "self", returnsPrimitive: null })], vi = [new ei({ callsArgs: [0], mutatesSelfAsArray: true, returns: "self", returnsPrimitive: null })], Si = new oi({ __proto__: null, at: xi, concat: fi, copyWithin: bi, entries: fi, every: ui, fill: bi, filter: mi, find: Ei, findIndex: di, findLast: Ei, findLastIndex: di, flat: fi, flatMap: mi, forEach: Ei, includes: ti, indexOf: ii, join: si, keys: ni, lastIndexOf: ii, map: mi, pop: yi, push: gi, reduce: Ei, reduceRight: Ei, reverse: bi, shift: yi, slice: fi, some: ui, sort: vi, splice: pi, toLocaleString: si, toString: si, unshift: gi, values: xi }, hi, true);
class Ai extends Zs {
  constructor() {
    super(...arguments), this.objectEntity = null;
  }
  deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2) {
    this.getObjectEntity().deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2);
  }
  deoptimizePath(e3) {
    this.getObjectEntity().deoptimizePath(e3);
  }
  getLiteralValueAtPath(e3, t2, s2) {
    return this.getObjectEntity().getLiteralValueAtPath(e3, t2, s2);
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    return this.getObjectEntity().getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return this.getObjectEntity().hasEffectsOnInteractionAtPath(e3, t2, s2);
  }
  applyDeoptimizations() {
    this.deoptimized = true;
    let e3 = false;
    for (let t2 = 0; t2 < this.elements.length; t2++) {
      const s2 = this.elements[t2];
      s2 && (e3 || s2 instanceof Js) && (e3 = true, s2.deoptimizePath(Y));
    }
    this.context.requestTreeshakingPass();
  }
  getObjectEntity() {
    if (null !== this.objectEntity)
      return this.objectEntity;
    const e3 = [{ key: "length", kind: "init", property: as }];
    let t2 = false;
    for (let s2 = 0; s2 < this.elements.length; s2++) {
      const i2 = this.elements[s2];
      t2 || i2 instanceof Js ? i2 && (t2 = true, e3.unshift({ key: q, kind: "init", property: i2 })) : i2 ? e3.push({ key: String(s2), kind: "init", property: i2 }) : e3.push({ key: String(s2), kind: "init", property: is });
    }
    return this.objectEntity = new oi(e3, Si);
  }
}
class ki extends Zs {
  addExportedVariables(e3, t2) {
    for (const s2 of this.elements)
      s2 == null ? void 0 : s2.addExportedVariables(e3, t2);
  }
  declare(e3) {
    const t2 = [];
    for (const s2 of this.elements)
      null !== s2 && t2.push(...s2.declare(e3, re));
    return t2;
  }
  deoptimizePath() {
    for (const e3 of this.elements)
      e3 == null ? void 0 : e3.deoptimizePath(K);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    for (const e4 of this.elements)
      if (e4 == null ? void 0 : e4.hasEffectsOnInteractionAtPath(K, t2, s2))
        return true;
    return false;
  }
  markDeclarationReached() {
    for (const e3 of this.elements)
      e3 == null ? void 0 : e3.markDeclarationReached();
  }
}
class Ii extends ue {
  constructor(e3, t2, s2, i2) {
    super(e3), this.init = s2, this.calledFromTryStatement = false, this.additionalInitializers = null, this.expressionsToBeDeoptimized = [], this.declarations = t2 ? [t2] : [], this.deoptimizationTracker = i2.deoptimizationTracker, this.module = i2.module;
  }
  addDeclaration(e3, t2) {
    this.declarations.push(e3), this.markInitializersForDeoptimization().push(t2);
  }
  consolidateInitializers() {
    if (this.additionalInitializers) {
      for (const e3 of this.additionalInitializers)
        e3.deoptimizePath(Y);
      this.additionalInitializers = null;
    }
  }
  deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2) {
    this.isReassigned ? ae(e3) : s2.withTrackedEntityAtPath(t2, this.init, () => this.init.deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2), void 0);
  }
  deoptimizePath(e3) {
    if (!this.isReassigned && !this.deoptimizationTracker.trackEntityAtPathAndGetIfTracked(e3, this))
      if (0 === e3.length) {
        if (!this.isReassigned) {
          this.isReassigned = true;
          const e4 = this.expressionsToBeDeoptimized;
          this.expressionsToBeDeoptimized = me;
          for (const t2 of e4)
            t2.deoptimizeCache();
          this.init.deoptimizePath(Y);
        }
      } else
        this.init.deoptimizePath(e3);
  }
  getLiteralValueAtPath(e3, t2, s2) {
    return this.isReassigned ? se : t2.withTrackedEntityAtPath(e3, this.init, () => (this.expressionsToBeDeoptimized.push(s2), this.init.getLiteralValueAtPath(e3, t2, s2)), se);
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    return this.isReassigned ? oe : s2.withTrackedEntityAtPath(e3, this.init, () => (this.expressionsToBeDeoptimized.push(i2), this.init.getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2)), oe);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    switch (t2.type) {
      case 0:
        return !!this.isReassigned || !s2.accessed.trackEntityAtPathAndGetIfTracked(e3, this) && this.init.hasEffectsOnInteractionAtPath(e3, t2, s2);
      case 1:
        return !!this.included || 0 !== e3.length && (!!this.isReassigned || !s2.assigned.trackEntityAtPathAndGetIfTracked(e3, this) && this.init.hasEffectsOnInteractionAtPath(e3, t2, s2));
      case 2:
        return !!this.isReassigned || !(t2.withNew ? s2.instantiated : s2.called).trackEntityAtPathAndGetIfTracked(e3, t2.args, this) && this.init.hasEffectsOnInteractionAtPath(e3, t2, s2);
    }
  }
  include() {
    if (!this.included) {
      this.included = true;
      for (const e3 of this.declarations) {
        e3.included || e3.include(es(), false);
        let t2 = e3.parent;
        for (; !t2.included && (t2.included = true, t2.type !== Ls); )
          t2 = t2.parent;
      }
    }
  }
  includeCallArguments(e3, t2) {
    if (this.isReassigned || e3.includedCallArguments.has(this.init))
      for (const s2 of t2)
        s2.include(e3, false);
    else
      e3.includedCallArguments.add(this.init), this.init.includeCallArguments(e3, t2), e3.includedCallArguments.delete(this.init);
  }
  markCalledFromTryStatement() {
    this.calledFromTryStatement = true;
  }
  markInitializersForDeoptimization() {
    return null === this.additionalInitializers && (this.additionalInitializers = [this.init], this.init = re, this.isReassigned = true), this.additionalInitializers;
  }
  mergeDeclarations(e3) {
    const { declarations: t2 } = this;
    for (const s3 of e3.declarations)
      t2.push(s3);
    const s2 = this.markInitializersForDeoptimization();
    if (s2.push(e3.init), e3.additionalInitializers)
      for (const t3 of e3.additionalInitializers)
        s2.push(t3);
  }
}
const wi = me, Pi = /* @__PURE__ */ new Set([G]), Ci = new J(), $i = /* @__PURE__ */ new Set([re]);
class Ni extends Ii {
  constructor(e3, t2, s2) {
    super(e3, t2, re, s2), this.deoptimizationInteractions = [], this.deoptimizations = new J(), this.deoptimizedFields = /* @__PURE__ */ new Set(), this.entitiesToBeDeoptimized = /* @__PURE__ */ new Set();
  }
  addEntityToBeDeoptimized(e3) {
    if (e3 === re) {
      if (!this.entitiesToBeDeoptimized.has(re)) {
        this.entitiesToBeDeoptimized.add(re);
        for (const { interaction: e4 } of this.deoptimizationInteractions)
          ae(e4);
        this.deoptimizationInteractions = wi;
      }
    } else if (this.deoptimizedFields.has(G))
      e3.deoptimizePath(Y);
    else if (!this.entitiesToBeDeoptimized.has(e3)) {
      this.entitiesToBeDeoptimized.add(e3);
      for (const t2 of this.deoptimizedFields)
        e3.deoptimizePath([t2]);
      for (const { interaction: t2, path: s2 } of this.deoptimizationInteractions)
        e3.deoptimizeArgumentsOnInteractionAtPath(t2, s2, ee);
    }
  }
  deoptimizeArgumentsOnInteractionAtPath(e3, t2) {
    if (t2.length >= 2 || this.entitiesToBeDeoptimized.has(re) || this.deoptimizationInteractions.length >= 20 || 1 === t2.length && (this.deoptimizedFields.has(G) || 2 === e3.type && this.deoptimizedFields.has(t2[0])))
      ae(e3);
    else if (!this.deoptimizations.trackEntityAtPathAndGetIfTracked(t2, e3.args)) {
      for (const s2 of this.entitiesToBeDeoptimized)
        s2.deoptimizeArgumentsOnInteractionAtPath(e3, t2, ee);
      this.entitiesToBeDeoptimized.has(re) || this.deoptimizationInteractions.push({ interaction: e3, path: t2 });
    }
  }
  deoptimizePath(e3) {
    if (0 === e3.length || this.deoptimizedFields.has(G))
      return;
    const t2 = e3[0];
    if (!this.deoptimizedFields.has(t2)) {
      this.deoptimizedFields.add(t2);
      for (const t3 of this.entitiesToBeDeoptimized)
        t3.deoptimizePath(e3);
      t2 === G && (this.deoptimizationInteractions = wi, this.deoptimizations = Ci, this.deoptimizedFields = Pi, this.entitiesToBeDeoptimized = $i);
    }
  }
  getReturnExpressionWhenCalledAtPath(e3) {
    return 0 === e3.length ? this.deoptimizePath(Y) : this.deoptimizedFields.has(e3[0]) || this.deoptimizePath([e3[0]]), oe;
  }
}
const _i = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_$", Ri = 64;
function Oi(e3) {
  let t2 = "";
  do {
    const s2 = e3 % Ri;
    e3 = e3 / Ri | 0, t2 = _i[s2] + t2;
  } while (0 !== e3);
  return t2;
}
function Di(e3, t2, s2) {
  let i2 = e3, n2 = 1;
  for (; t2.has(i2) || ye.has(i2) || (s2 == null ? void 0 : s2.has(i2)); )
    i2 = `${e3}$${Oi(n2++)}`;
  return t2.add(i2), i2;
}
let Li = class {
  constructor() {
    this.children = [], this.variables = /* @__PURE__ */ new Map();
  }
  addDeclaration(e3, t2, s2, i2) {
    const n2 = e3.name;
    let r2 = this.variables.get(n2);
    return r2 ? r2.addDeclaration(e3, s2) : (r2 = new Ii(e3.name, e3, s2 || is, t2), this.variables.set(n2, r2)), r2;
  }
  contains(e3) {
    return this.variables.has(e3);
  }
  findVariable(e3) {
    throw new Error("Internal Error: findVariable needs to be implemented by a subclass");
  }
};
class Ti extends Li {
  constructor(e3) {
    super(), this.accessedOutsideVariables = /* @__PURE__ */ new Map(), this.parent = e3, e3.children.push(this);
  }
  addAccessedDynamicImport(e3) {
    (this.accessedDynamicImports || (this.accessedDynamicImports = /* @__PURE__ */ new Set())).add(e3), this.parent instanceof Ti && this.parent.addAccessedDynamicImport(e3);
  }
  addAccessedGlobals(e3, t2) {
    const s2 = t2.get(this) || /* @__PURE__ */ new Set();
    for (const t3 of e3)
      s2.add(t3);
    t2.set(this, s2), this.parent instanceof Ti && this.parent.addAccessedGlobals(e3, t2);
  }
  addNamespaceMemberAccess(e3, t2) {
    this.accessedOutsideVariables.set(e3, t2), this.parent.addNamespaceMemberAccess(e3, t2);
  }
  addReturnExpression(e3) {
    this.parent instanceof Ti && this.parent.addReturnExpression(e3);
  }
  addUsedOutsideNames(e3, t2, s2, i2) {
    for (const i3 of this.accessedOutsideVariables.values())
      i3.included && (e3.add(i3.getBaseVariableName()), "system" === t2 && s2.has(i3) && e3.add("exports"));
    const n2 = i2.get(this);
    if (n2)
      for (const t3 of n2)
        e3.add(t3);
  }
  contains(e3) {
    return this.variables.has(e3) || this.parent.contains(e3);
  }
  deconflict(e3, t2, s2) {
    const i2 = /* @__PURE__ */ new Set();
    if (this.addUsedOutsideNames(i2, e3, t2, s2), this.accessedDynamicImports)
      for (const e4 of this.accessedDynamicImports)
        e4.inlineNamespace && i2.add(e4.inlineNamespace.getBaseVariableName());
    for (const [e4, t3] of this.variables)
      (t3.included || t3.alwaysRendered) && t3.setRenderNames(null, Di(e4, i2, t3.forbiddenNames));
    for (const i3 of this.children)
      i3.deconflict(e3, t2, s2);
  }
  findLexicalBoundary() {
    return this.parent.findLexicalBoundary();
  }
  findVariable(e3) {
    const t2 = this.variables.get(e3) || this.accessedOutsideVariables.get(e3);
    if (t2)
      return t2;
    const s2 = this.parent.findVariable(e3);
    return this.accessedOutsideVariables.set(e3, s2), s2;
  }
}
class Mi extends Ti {
  constructor(e3, t2) {
    super(e3), this.parameters = [], this.hasRest = false, this.context = t2, this.hoistedBodyVarScope = new Ti(this);
  }
  addParameterDeclaration(e3) {
    const { name: t2 } = e3, s2 = new Ni(t2, e3, this.context), i2 = this.hoistedBodyVarScope.variables.get(t2);
    return i2 && (this.hoistedBodyVarScope.variables.set(t2, s2), s2.mergeDeclarations(i2)), this.variables.set(t2, s2), s2;
  }
  addParameterVariables(e3, t2) {
    this.parameters = e3;
    for (const t3 of e3)
      for (const e4 of t3)
        e4.alwaysRendered = true;
    this.hasRest = t2;
  }
  includeCallArguments(e3, t2) {
    let s2 = false, i2 = false;
    const n2 = this.hasRest && this.parameters[this.parameters.length - 1];
    for (const s3 of t2)
      if (s3 instanceof Js) {
        for (const s4 of t2)
          s4.include(e3, false);
        break;
      }
    for (let r2 = t2.length - 1; r2 >= 0; r2--) {
      const o2 = this.parameters[r2] || n2, a2 = t2[r2];
      if (o2)
        if (s2 = false, 0 === o2.length)
          i2 = true;
        else
          for (const e4 of o2)
            e4.included && (i2 = true), e4.calledFromTryStatement && (s2 = true);
      !i2 && a2.shouldBeIncluded(e3) && (i2 = true), i2 && a2.include(e3, s2);
    }
  }
}
class Vi extends Mi {
  constructor() {
    super(...arguments), this.returnExpression = null, this.returnExpressions = [];
  }
  addReturnExpression(e3) {
    this.returnExpressions.push(e3);
  }
  getReturnExpression() {
    return null === this.returnExpression && this.updateReturnExpression(), this.returnExpression;
  }
  updateReturnExpression() {
    if (1 === this.returnExpressions.length)
      this.returnExpression = this.returnExpressions[0];
    else {
      this.returnExpression = re;
      for (const e3 of this.returnExpressions)
        e3.deoptimizePath(Y);
    }
  }
}
function Bi(e3, t2) {
  if ("MemberExpression" === e3.type)
    return !e3.computed && Bi(e3.object, e3);
  if ("Identifier" === e3.type) {
    if (!t2)
      return true;
    switch (t2.type) {
      case "MemberExpression":
        return t2.computed || e3 === t2.object;
      case "MethodDefinition":
        return t2.computed;
      case "PropertyDefinition":
      case "Property":
        return t2.computed || e3 === t2.value;
      case "ExportSpecifier":
      case "ImportSpecifier":
        return e3 === t2.local;
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
const zi = Symbol("PureFunction"), Fi = () => {
}, ji = Symbol("Value Properties"), Ui = () => ie, Gi = () => true, Wi = { deoptimizeArgumentsOnCall: Fi, getLiteralValue: Ui, hasEffectsWhenCalled: () => false }, qi = { deoptimizeArgumentsOnCall: Fi, getLiteralValue: Ui, hasEffectsWhenCalled: Gi }, Hi = { __proto__: null, [ji]: qi }, Ki = { __proto__: null, [ji]: Wi }, Yi = { __proto__: null, [ji]: { deoptimizeArgumentsOnCall({ args: [, e3] }) {
  e3 == null ? void 0 : e3.deoptimizePath(Y);
}, getLiteralValue: Ui, hasEffectsWhenCalled: ({ args: e3 }, t2) => e3.length <= 1 || e3[1].hasEffectsOnInteractionAtPath(X, he, t2) } }, Xi = { __proto__: null, [ji]: qi, prototype: Hi }, Qi = { __proto__: null, [ji]: Wi, prototype: Hi }, Zi = { __proto__: null, [ji]: { deoptimizeArgumentsOnCall: Fi, getLiteralValue: Ui, hasEffectsWhenCalled: ({ args: e3 }) => e3.length > 1 && !(e3[1] instanceof Ai) }, prototype: Hi }, Ji = { __proto__: null, [ji]: Wi, from: Hi, of: Ki, prototype: Hi }, en = { __proto__: null, [ji]: Wi, supportedLocalesOf: Qi }, tn = { global: Hi, globalThis: Hi, self: Hi, window: Hi, __proto__: null, [ji]: qi, Array: { __proto__: null, [ji]: qi, from: Hi, isArray: Ki, of: Ki, prototype: Hi }, ArrayBuffer: { __proto__: null, [ji]: Wi, isView: Ki, prototype: Hi }, Atomics: Hi, BigInt: Xi, BigInt64Array: Xi, BigUint64Array: Xi, Boolean: Qi, constructor: Xi, DataView: Qi, Date: { __proto__: null, [ji]: Wi, now: Ki, parse: Ki, prototype: Hi, UTC: Ki }, decodeURI: Ki, decodeURIComponent: Ki, encodeURI: Ki, encodeURIComponent: Ki, Error: Qi, escape: Ki, eval: Hi, EvalError: Qi, Float32Array: Ji, Float64Array: Ji, Function: Xi, hasOwnProperty: Hi, Infinity: Hi, Int16Array: Ji, Int32Array: Ji, Int8Array: Ji, isFinite: Ki, isNaN: Ki, isPrototypeOf: Hi, JSON: Hi, Map: Zi, Math: { __proto__: null, [ji]: qi, abs: Ki, acos: Ki, acosh: Ki, asin: Ki, asinh: Ki, atan: Ki, atan2: Ki, atanh: Ki, cbrt: Ki, ceil: Ki, clz32: Ki, cos: Ki, cosh: Ki, exp: Ki, expm1: Ki, floor: Ki, fround: Ki, hypot: Ki, imul: Ki, log: Ki, log10: Ki, log1p: Ki, log2: Ki, max: Ki, min: Ki, pow: Ki, random: Ki, round: Ki, sign: Ki, sin: Ki, sinh: Ki, sqrt: Ki, tan: Ki, tanh: Ki, trunc: Ki }, NaN: Hi, Number: { __proto__: null, [ji]: Wi, isFinite: Ki, isInteger: Ki, isNaN: Ki, isSafeInteger: Ki, parseFloat: Ki, parseInt: Ki, prototype: Hi }, Object: { __proto__: null, [ji]: Wi, create: Ki, defineProperty: Yi, defineProperties: Yi, freeze: Yi, getOwnPropertyDescriptor: Ki, getOwnPropertyDescriptors: Ki, getOwnPropertyNames: Ki, getOwnPropertySymbols: Ki, getPrototypeOf: Ki, hasOwn: Ki, is: Ki, isExtensible: Ki, isFrozen: Ki, isSealed: Ki, keys: Ki, fromEntries: Hi, entries: Ki, prototype: Hi }, parseFloat: Ki, parseInt: Ki, Promise: { __proto__: null, [ji]: qi, all: Hi, allSettled: Hi, any: Hi, prototype: Hi, race: Hi, reject: Hi, resolve: Hi }, propertyIsEnumerable: Hi, Proxy: Hi, RangeError: Qi, ReferenceError: Qi, Reflect: Hi, RegExp: Qi, Set: Zi, SharedArrayBuffer: Xi, String: { __proto__: null, [ji]: Wi, fromCharCode: Ki, fromCodePoint: Ki, prototype: Hi, raw: Ki }, Symbol: { __proto__: null, [ji]: Wi, for: Ki, keyFor: Ki, prototype: Hi, toStringTag: { __proto__: null, [ji]: { deoptimizeArgumentsOnCall: Fi, getLiteralValue: () => H, hasEffectsWhenCalled: Gi } } }, SyntaxError: Qi, toLocaleString: Hi, toString: Hi, TypeError: Qi, Uint16Array: Ji, Uint32Array: Ji, Uint8Array: Ji, Uint8ClampedArray: Ji, unescape: Ki, URIError: Qi, valueOf: Hi, WeakMap: Zi, WeakSet: Zi, clearInterval: Xi, clearTimeout: Xi, console: { __proto__: null, [ji]: qi, assert: Xi, clear: Xi, count: Xi, countReset: Xi, debug: Xi, dir: Xi, dirxml: Xi, error: Xi, exception: Xi, group: Xi, groupCollapsed: Xi, groupEnd: Xi, info: Xi, log: Xi, table: Xi, time: Xi, timeEnd: Xi, timeLog: Xi, trace: Xi, warn: Xi }, Intl: { __proto__: null, [ji]: qi, Collator: en, DateTimeFormat: en, ListFormat: en, NumberFormat: en, PluralRules: en, RelativeTimeFormat: en }, setInterval: Xi, setTimeout: Xi, TextDecoder: Xi, TextEncoder: Xi, URL: Xi, URLSearchParams: Xi, AbortController: Xi, AbortSignal: Xi, addEventListener: Hi, alert: Hi, AnalyserNode: Xi, Animation: Xi, AnimationEvent: Xi, applicationCache: Hi, ApplicationCache: Xi, ApplicationCacheErrorEvent: Xi, atob: Hi, Attr: Xi, Audio: Xi, AudioBuffer: Xi, AudioBufferSourceNode: Xi, AudioContext: Xi, AudioDestinationNode: Xi, AudioListener: Xi, AudioNode: Xi, AudioParam: Xi, AudioProcessingEvent: Xi, AudioScheduledSourceNode: Xi, AudioWorkletNode: Xi, BarProp: Xi, BaseAudioContext: Xi, BatteryManager: Xi, BeforeUnloadEvent: Xi, BiquadFilterNode: Xi, Blob: Xi, BlobEvent: Xi, blur: Hi, BroadcastChannel: Xi, btoa: Hi, ByteLengthQueuingStrategy: Xi, Cache: Xi, caches: Hi, CacheStorage: Xi, cancelAnimationFrame: Hi, cancelIdleCallback: Hi, CanvasCaptureMediaStreamTrack: Xi, CanvasGradient: Xi, CanvasPattern: Xi, CanvasRenderingContext2D: Xi, ChannelMergerNode: Xi, ChannelSplitterNode: Xi, CharacterData: Xi, clientInformation: Hi, ClipboardEvent: Xi, close: Hi, closed: Hi, CloseEvent: Xi, Comment: Xi, CompositionEvent: Xi, confirm: Hi, ConstantSourceNode: Xi, ConvolverNode: Xi, CountQueuingStrategy: Xi, createImageBitmap: Hi, Credential: Xi, CredentialsContainer: Xi, crypto: Hi, Crypto: Xi, CryptoKey: Xi, CSS: Xi, CSSConditionRule: Xi, CSSFontFaceRule: Xi, CSSGroupingRule: Xi, CSSImportRule: Xi, CSSKeyframeRule: Xi, CSSKeyframesRule: Xi, CSSMediaRule: Xi, CSSNamespaceRule: Xi, CSSPageRule: Xi, CSSRule: Xi, CSSRuleList: Xi, CSSStyleDeclaration: Xi, CSSStyleRule: Xi, CSSStyleSheet: Xi, CSSSupportsRule: Xi, CustomElementRegistry: Xi, customElements: Hi, CustomEvent: Xi, DataTransfer: Xi, DataTransferItem: Xi, DataTransferItemList: Xi, defaultstatus: Hi, defaultStatus: Hi, DelayNode: Xi, DeviceMotionEvent: Xi, DeviceOrientationEvent: Xi, devicePixelRatio: Hi, dispatchEvent: Hi, document: Hi, Document: Xi, DocumentFragment: Xi, DocumentType: Xi, DOMError: Xi, DOMException: Xi, DOMImplementation: Xi, DOMMatrix: Xi, DOMMatrixReadOnly: Xi, DOMParser: Xi, DOMPoint: Xi, DOMPointReadOnly: Xi, DOMQuad: Xi, DOMRect: Xi, DOMRectReadOnly: Xi, DOMStringList: Xi, DOMStringMap: Xi, DOMTokenList: Xi, DragEvent: Xi, DynamicsCompressorNode: Xi, Element: Xi, ErrorEvent: Xi, Event: Xi, EventSource: Xi, EventTarget: Xi, external: Hi, fetch: Hi, File: Xi, FileList: Xi, FileReader: Xi, find: Hi, focus: Hi, FocusEvent: Xi, FontFace: Xi, FontFaceSetLoadEvent: Xi, FormData: Xi, frames: Hi, GainNode: Xi, Gamepad: Xi, GamepadButton: Xi, GamepadEvent: Xi, getComputedStyle: Hi, getSelection: Hi, HashChangeEvent: Xi, Headers: Xi, history: Hi, History: Xi, HTMLAllCollection: Xi, HTMLAnchorElement: Xi, HTMLAreaElement: Xi, HTMLAudioElement: Xi, HTMLBaseElement: Xi, HTMLBodyElement: Xi, HTMLBRElement: Xi, HTMLButtonElement: Xi, HTMLCanvasElement: Xi, HTMLCollection: Xi, HTMLContentElement: Xi, HTMLDataElement: Xi, HTMLDataListElement: Xi, HTMLDetailsElement: Xi, HTMLDialogElement: Xi, HTMLDirectoryElement: Xi, HTMLDivElement: Xi, HTMLDListElement: Xi, HTMLDocument: Xi, HTMLElement: Xi, HTMLEmbedElement: Xi, HTMLFieldSetElement: Xi, HTMLFontElement: Xi, HTMLFormControlsCollection: Xi, HTMLFormElement: Xi, HTMLFrameElement: Xi, HTMLFrameSetElement: Xi, HTMLHeadElement: Xi, HTMLHeadingElement: Xi, HTMLHRElement: Xi, HTMLHtmlElement: Xi, HTMLIFrameElement: Xi, HTMLImageElement: Xi, HTMLInputElement: Xi, HTMLLabelElement: Xi, HTMLLegendElement: Xi, HTMLLIElement: Xi, HTMLLinkElement: Xi, HTMLMapElement: Xi, HTMLMarqueeElement: Xi, HTMLMediaElement: Xi, HTMLMenuElement: Xi, HTMLMetaElement: Xi, HTMLMeterElement: Xi, HTMLModElement: Xi, HTMLObjectElement: Xi, HTMLOListElement: Xi, HTMLOptGroupElement: Xi, HTMLOptionElement: Xi, HTMLOptionsCollection: Xi, HTMLOutputElement: Xi, HTMLParagraphElement: Xi, HTMLParamElement: Xi, HTMLPictureElement: Xi, HTMLPreElement: Xi, HTMLProgressElement: Xi, HTMLQuoteElement: Xi, HTMLScriptElement: Xi, HTMLSelectElement: Xi, HTMLShadowElement: Xi, HTMLSlotElement: Xi, HTMLSourceElement: Xi, HTMLSpanElement: Xi, HTMLStyleElement: Xi, HTMLTableCaptionElement: Xi, HTMLTableCellElement: Xi, HTMLTableColElement: Xi, HTMLTableElement: Xi, HTMLTableRowElement: Xi, HTMLTableSectionElement: Xi, HTMLTemplateElement: Xi, HTMLTextAreaElement: Xi, HTMLTimeElement: Xi, HTMLTitleElement: Xi, HTMLTrackElement: Xi, HTMLUListElement: Xi, HTMLUnknownElement: Xi, HTMLVideoElement: Xi, IDBCursor: Xi, IDBCursorWithValue: Xi, IDBDatabase: Xi, IDBFactory: Xi, IDBIndex: Xi, IDBKeyRange: Xi, IDBObjectStore: Xi, IDBOpenDBRequest: Xi, IDBRequest: Xi, IDBTransaction: Xi, IDBVersionChangeEvent: Xi, IdleDeadline: Xi, IIRFilterNode: Xi, Image: Xi, ImageBitmap: Xi, ImageBitmapRenderingContext: Xi, ImageCapture: Xi, ImageData: Xi, indexedDB: Hi, innerHeight: Hi, innerWidth: Hi, InputEvent: Xi, IntersectionObserver: Xi, IntersectionObserverEntry: Xi, isSecureContext: Hi, KeyboardEvent: Xi, KeyframeEffect: Xi, length: Hi, localStorage: Hi, location: Hi, Location: Xi, locationbar: Hi, matchMedia: Hi, MediaDeviceInfo: Xi, MediaDevices: Xi, MediaElementAudioSourceNode: Xi, MediaEncryptedEvent: Xi, MediaError: Xi, MediaKeyMessageEvent: Xi, MediaKeySession: Xi, MediaKeyStatusMap: Xi, MediaKeySystemAccess: Xi, MediaList: Xi, MediaQueryList: Xi, MediaQueryListEvent: Xi, MediaRecorder: Xi, MediaSettingsRange: Xi, MediaSource: Xi, MediaStream: Xi, MediaStreamAudioDestinationNode: Xi, MediaStreamAudioSourceNode: Xi, MediaStreamEvent: Xi, MediaStreamTrack: Xi, MediaStreamTrackEvent: Xi, menubar: Hi, MessageChannel: Xi, MessageEvent: Xi, MessagePort: Xi, MIDIAccess: Xi, MIDIConnectionEvent: Xi, MIDIInput: Xi, MIDIInputMap: Xi, MIDIMessageEvent: Xi, MIDIOutput: Xi, MIDIOutputMap: Xi, MIDIPort: Xi, MimeType: Xi, MimeTypeArray: Xi, MouseEvent: Xi, moveBy: Hi, moveTo: Hi, MutationEvent: Xi, MutationObserver: Xi, MutationRecord: Xi, name: Hi, NamedNodeMap: Xi, NavigationPreloadManager: Xi, navigator: Hi, Navigator: Xi, NetworkInformation: Xi, Node: Xi, NodeFilter: Hi, NodeIterator: Xi, NodeList: Xi, Notification: Xi, OfflineAudioCompletionEvent: Xi, OfflineAudioContext: Xi, offscreenBuffering: Hi, OffscreenCanvas: Xi, open: Hi, openDatabase: Hi, Option: Xi, origin: Hi, OscillatorNode: Xi, outerHeight: Hi, outerWidth: Hi, PageTransitionEvent: Xi, pageXOffset: Hi, pageYOffset: Hi, PannerNode: Xi, parent: Hi, Path2D: Xi, PaymentAddress: Xi, PaymentRequest: Xi, PaymentRequestUpdateEvent: Xi, PaymentResponse: Xi, performance: Hi, Performance: Xi, PerformanceEntry: Xi, PerformanceLongTaskTiming: Xi, PerformanceMark: Xi, PerformanceMeasure: Xi, PerformanceNavigation: Xi, PerformanceNavigationTiming: Xi, PerformanceObserver: Xi, PerformanceObserverEntryList: Xi, PerformancePaintTiming: Xi, PerformanceResourceTiming: Xi, PerformanceTiming: Xi, PeriodicWave: Xi, Permissions: Xi, PermissionStatus: Xi, personalbar: Hi, PhotoCapabilities: Xi, Plugin: Xi, PluginArray: Xi, PointerEvent: Xi, PopStateEvent: Xi, postMessage: Hi, Presentation: Xi, PresentationAvailability: Xi, PresentationConnection: Xi, PresentationConnectionAvailableEvent: Xi, PresentationConnectionCloseEvent: Xi, PresentationConnectionList: Xi, PresentationReceiver: Xi, PresentationRequest: Xi, print: Hi, ProcessingInstruction: Xi, ProgressEvent: Xi, PromiseRejectionEvent: Xi, prompt: Hi, PushManager: Xi, PushSubscription: Xi, PushSubscriptionOptions: Xi, queueMicrotask: Hi, RadioNodeList: Xi, Range: Xi, ReadableStream: Xi, RemotePlayback: Xi, removeEventListener: Hi, Request: Xi, requestAnimationFrame: Hi, requestIdleCallback: Hi, resizeBy: Hi, ResizeObserver: Xi, ResizeObserverEntry: Xi, resizeTo: Hi, Response: Xi, RTCCertificate: Xi, RTCDataChannel: Xi, RTCDataChannelEvent: Xi, RTCDtlsTransport: Xi, RTCIceCandidate: Xi, RTCIceTransport: Xi, RTCPeerConnection: Xi, RTCPeerConnectionIceEvent: Xi, RTCRtpReceiver: Xi, RTCRtpSender: Xi, RTCSctpTransport: Xi, RTCSessionDescription: Xi, RTCStatsReport: Xi, RTCTrackEvent: Xi, screen: Hi, Screen: Xi, screenLeft: Hi, ScreenOrientation: Xi, screenTop: Hi, screenX: Hi, screenY: Hi, ScriptProcessorNode: Xi, scroll: Hi, scrollbars: Hi, scrollBy: Hi, scrollTo: Hi, scrollX: Hi, scrollY: Hi, SecurityPolicyViolationEvent: Xi, Selection: Xi, ServiceWorker: Xi, ServiceWorkerContainer: Xi, ServiceWorkerRegistration: Xi, sessionStorage: Hi, ShadowRoot: Xi, SharedWorker: Xi, SourceBuffer: Xi, SourceBufferList: Xi, speechSynthesis: Hi, SpeechSynthesisEvent: Xi, SpeechSynthesisUtterance: Xi, StaticRange: Xi, status: Hi, statusbar: Hi, StereoPannerNode: Xi, stop: Hi, Storage: Xi, StorageEvent: Xi, StorageManager: Xi, styleMedia: Hi, StyleSheet: Xi, StyleSheetList: Xi, SubtleCrypto: Xi, SVGAElement: Xi, SVGAngle: Xi, SVGAnimatedAngle: Xi, SVGAnimatedBoolean: Xi, SVGAnimatedEnumeration: Xi, SVGAnimatedInteger: Xi, SVGAnimatedLength: Xi, SVGAnimatedLengthList: Xi, SVGAnimatedNumber: Xi, SVGAnimatedNumberList: Xi, SVGAnimatedPreserveAspectRatio: Xi, SVGAnimatedRect: Xi, SVGAnimatedString: Xi, SVGAnimatedTransformList: Xi, SVGAnimateElement: Xi, SVGAnimateMotionElement: Xi, SVGAnimateTransformElement: Xi, SVGAnimationElement: Xi, SVGCircleElement: Xi, SVGClipPathElement: Xi, SVGComponentTransferFunctionElement: Xi, SVGDefsElement: Xi, SVGDescElement: Xi, SVGDiscardElement: Xi, SVGElement: Xi, SVGEllipseElement: Xi, SVGFEBlendElement: Xi, SVGFEColorMatrixElement: Xi, SVGFEComponentTransferElement: Xi, SVGFECompositeElement: Xi, SVGFEConvolveMatrixElement: Xi, SVGFEDiffuseLightingElement: Xi, SVGFEDisplacementMapElement: Xi, SVGFEDistantLightElement: Xi, SVGFEDropShadowElement: Xi, SVGFEFloodElement: Xi, SVGFEFuncAElement: Xi, SVGFEFuncBElement: Xi, SVGFEFuncGElement: Xi, SVGFEFuncRElement: Xi, SVGFEGaussianBlurElement: Xi, SVGFEImageElement: Xi, SVGFEMergeElement: Xi, SVGFEMergeNodeElement: Xi, SVGFEMorphologyElement: Xi, SVGFEOffsetElement: Xi, SVGFEPointLightElement: Xi, SVGFESpecularLightingElement: Xi, SVGFESpotLightElement: Xi, SVGFETileElement: Xi, SVGFETurbulenceElement: Xi, SVGFilterElement: Xi, SVGForeignObjectElement: Xi, SVGGElement: Xi, SVGGeometryElement: Xi, SVGGradientElement: Xi, SVGGraphicsElement: Xi, SVGImageElement: Xi, SVGLength: Xi, SVGLengthList: Xi, SVGLinearGradientElement: Xi, SVGLineElement: Xi, SVGMarkerElement: Xi, SVGMaskElement: Xi, SVGMatrix: Xi, SVGMetadataElement: Xi, SVGMPathElement: Xi, SVGNumber: Xi, SVGNumberList: Xi, SVGPathElement: Xi, SVGPatternElement: Xi, SVGPoint: Xi, SVGPointList: Xi, SVGPolygonElement: Xi, SVGPolylineElement: Xi, SVGPreserveAspectRatio: Xi, SVGRadialGradientElement: Xi, SVGRect: Xi, SVGRectElement: Xi, SVGScriptElement: Xi, SVGSetElement: Xi, SVGStopElement: Xi, SVGStringList: Xi, SVGStyleElement: Xi, SVGSVGElement: Xi, SVGSwitchElement: Xi, SVGSymbolElement: Xi, SVGTextContentElement: Xi, SVGTextElement: Xi, SVGTextPathElement: Xi, SVGTextPositioningElement: Xi, SVGTitleElement: Xi, SVGTransform: Xi, SVGTransformList: Xi, SVGTSpanElement: Xi, SVGUnitTypes: Xi, SVGUseElement: Xi, SVGViewElement: Xi, TaskAttributionTiming: Xi, Text: Xi, TextEvent: Xi, TextMetrics: Xi, TextTrack: Xi, TextTrackCue: Xi, TextTrackCueList: Xi, TextTrackList: Xi, TimeRanges: Xi, toolbar: Hi, top: Hi, Touch: Xi, TouchEvent: Xi, TouchList: Xi, TrackEvent: Xi, TransitionEvent: Xi, TreeWalker: Xi, UIEvent: Xi, ValidityState: Xi, visualViewport: Hi, VisualViewport: Xi, VTTCue: Xi, WaveShaperNode: Xi, WebAssembly: Hi, WebGL2RenderingContext: Xi, WebGLActiveInfo: Xi, WebGLBuffer: Xi, WebGLContextEvent: Xi, WebGLFramebuffer: Xi, WebGLProgram: Xi, WebGLQuery: Xi, WebGLRenderbuffer: Xi, WebGLRenderingContext: Xi, WebGLSampler: Xi, WebGLShader: Xi, WebGLShaderPrecisionFormat: Xi, WebGLSync: Xi, WebGLTexture: Xi, WebGLTransformFeedback: Xi, WebGLUniformLocation: Xi, WebGLVertexArrayObject: Xi, WebSocket: Xi, WheelEvent: Xi, Window: Xi, Worker: Xi, WritableStream: Xi, XMLDocument: Xi, XMLHttpRequest: Xi, XMLHttpRequestEventTarget: Xi, XMLHttpRequestUpload: Xi, XMLSerializer: Xi, XPathEvaluator: Xi, XPathExpression: Xi, XPathResult: Xi, XSLTProcessor: Xi };
for (const e3 of ["window", "global", "self", "globalThis"])
  tn[e3] = tn;
function sn(e3) {
  let t2 = tn;
  for (const s2 of e3) {
    if ("string" != typeof s2)
      return null;
    if (t2 = t2[s2], !t2)
      return null;
  }
  return t2[ji];
}
class nn extends ue {
  constructor() {
    super(...arguments), this.isReassigned = true;
  }
  deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2) {
    switch (e3.type) {
      case 0:
      case 1:
        return void (sn([this.name, ...t2].slice(0, -1)) || super.deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2));
      case 2: {
        const i2 = sn([this.name, ...t2]);
        return void (i2 ? i2.deoptimizeArgumentsOnCall(e3) : super.deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2));
      }
    }
  }
  getLiteralValueAtPath(e3, t2, s2) {
    const i2 = sn([this.name, ...e3]);
    return i2 ? i2.getLiteralValue() : se;
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    switch (t2.type) {
      case 0:
        return 0 === e3.length ? "undefined" !== this.name && !sn([this.name]) : !sn([this.name, ...e3].slice(0, -1));
      case 1:
        return true;
      case 2: {
        const i2 = sn([this.name, ...e3]);
        return !i2 || i2.hasEffectsWhenCalled(t2, s2);
      }
    }
  }
}
const rn = { __proto__: null, class: true, const: true, let: true, var: true };
class on extends Zs {
  constructor() {
    super(...arguments), this.variable = null, this.isTDZAccess = null;
  }
  addExportedVariables(e3, t2) {
    t2.has(this.variable) && e3.push(this.variable);
  }
  bind() {
    !this.variable && Bi(this, this.parent) && (this.variable = this.scope.findVariable(this.name), this.variable.addReference(this));
  }
  declare(e3, t2) {
    let s2;
    const { treeshake: i2 } = this.context.options;
    switch (e3) {
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
        throw new Error(`Internal Error: Unexpected identifier kind ${e3}.`);
    }
    return s2.kind = e3, [this.variable = s2];
  }
  deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2) {
    this.variable.deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2);
  }
  deoptimizePath(e3) {
    var _a2;
    0 !== e3.length || this.scope.contains(this.name) || this.disallowImportReassignment(), (_a2 = this.variable) == null ? void 0 : _a2.deoptimizePath(e3);
  }
  getLiteralValueAtPath(e3, t2, s2) {
    return this.getVariableRespectingTDZ().getLiteralValueAtPath(e3, t2, s2);
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    const [n2, r2] = this.getVariableRespectingTDZ().getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2);
    return [n2, r2 || this.isPureFunction(e3)];
  }
  hasEffects(e3) {
    return this.deoptimized || this.applyDeoptimizations(), !(!this.isPossibleTDZ() || "var" === this.variable.kind) || this.context.options.treeshake.unknownGlobalSideEffects && this.variable instanceof nn && !this.isPureFunction(K) && this.variable.hasEffectsOnInteractionAtPath(K, le, e3);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    switch (t2.type) {
      case 0:
        return null !== this.variable && !this.isPureFunction(e3) && this.getVariableRespectingTDZ().hasEffectsOnInteractionAtPath(e3, t2, s2);
      case 1:
        return (e3.length > 0 ? this.getVariableRespectingTDZ() : this.variable).hasEffectsOnInteractionAtPath(e3, t2, s2);
      case 2:
        return !this.isPureFunction(e3) && this.getVariableRespectingTDZ().hasEffectsOnInteractionAtPath(e3, t2, s2);
    }
  }
  include() {
    this.deoptimized || this.applyDeoptimizations(), this.included || (this.included = true, null !== this.variable && this.context.includeVariableInModule(this.variable));
  }
  includeCallArguments(e3, t2) {
    this.variable.includeCallArguments(e3, t2);
  }
  isPossibleTDZ() {
    if (null !== this.isTDZAccess)
      return this.isTDZAccess;
    if (!(this.variable instanceof Ii && this.variable.kind && this.variable.kind in rn && this.variable.module === this.context.module))
      return this.isTDZAccess = false;
    let e3;
    return this.variable.declarations && 1 === this.variable.declarations.length && (e3 = this.variable.declarations[0]) && this.start < e3.start && an(this) === an(e3) ? this.isTDZAccess = true : this.variable.initReached ? this.isTDZAccess = false : this.isTDZAccess = true;
  }
  markDeclarationReached() {
    this.variable.initReached = true;
  }
  render(e3, { snippets: { getPropertyAccess: t2 }, useOriginalName: s2 }, { renderedParentType: i2, isCalleeOfRenderedParent: n2, isShorthandProperty: r2 } = pe) {
    if (this.variable) {
      const o2 = this.variable.getName(t2, s2);
      o2 !== this.name && (e3.overwrite(this.start, this.end, o2, { contentOnly: true, storeName: true }), r2 && e3.prependRight(this.start, `${this.name}: `)), "eval" === o2 && i2 === Is && n2 && e3.appendRight(this.start, "0, ");
    }
  }
  applyDeoptimizations() {
    this.deoptimized = true, this.variable instanceof Ii && (this.variable.consolidateInitializers(), this.context.requestTreeshakingPass());
  }
  disallowImportReassignment() {
    return this.context.error(Tt(this.name, this.context.module.id), this.start);
  }
  getVariableRespectingTDZ() {
    return this.isPossibleTDZ() ? re : this.variable;
  }
  isPureFunction(e3) {
    let t2 = this.context.manualPureFunctions[this.name];
    for (const s2 of e3) {
      if (!t2)
        return false;
      if (t2[zi])
        return true;
      t2 = t2[s2];
    }
    return t2 == null ? void 0 : t2[zi];
  }
}
function an(e3) {
  for (; e3 && !/^Program|Function/.test(e3.type); )
    e3 = e3.parent;
  return e3;
}
function ln(e3, t2, s2, i2) {
  if (t2.remove(s2, i2), e3.annotations)
    for (const i3 of e3.annotations) {
      if (!(i3.start < s2))
        return;
      t2.remove(i3.start, i3.end);
    }
}
function hn(e3, t2) {
  if (e3.annotations || e3.parent.type !== Ns || (e3 = e3.parent), e3.annotations)
    for (const s2 of e3.annotations)
      t2.remove(s2.start, s2.end);
}
const cn = { isNoStatement: true };
function un(e3, t2, s2 = 0) {
  let i2, n2;
  for (i2 = e3.indexOf(t2, s2); ; ) {
    if (-1 === (s2 = e3.indexOf("/", s2)) || s2 >= i2)
      return i2;
    n2 = e3.charCodeAt(++s2), ++s2, (s2 = 47 === n2 ? e3.indexOf("\n", s2) + 1 : e3.indexOf("*/", s2) + 2) > i2 && (i2 = e3.indexOf(t2, s2));
  }
}
const dn = /\S/g;
function pn(e3, t2) {
  dn.lastIndex = t2;
  return dn.exec(e3).index;
}
function fn(e3) {
  let t2, s2, i2 = 0;
  for (t2 = e3.indexOf("\n", i2); ; ) {
    if (i2 = e3.indexOf("/", i2), -1 === i2 || i2 > t2)
      return [t2, t2 + 1];
    if (s2 = e3.charCodeAt(i2 + 1), 47 === s2)
      return [i2, t2 + 1];
    i2 = e3.indexOf("*/", i2 + 3) + 2, i2 > t2 && (t2 = e3.indexOf("\n", i2));
  }
}
function mn(e3, t2, s2, i2, n2) {
  let r2, o2, a2, l2, h2 = e3[0], c2 = !h2.included || h2.needsBoundaries;
  c2 && (l2 = s2 + fn(t2.original.slice(s2, h2.start))[1]);
  for (let s3 = 1; s3 <= e3.length; s3++)
    r2 = h2, o2 = l2, a2 = c2, h2 = e3[s3], c2 = void 0 !== h2 && (!h2.included || h2.needsBoundaries), a2 || c2 ? (l2 = r2.end + fn(t2.original.slice(r2.end, void 0 === h2 ? i2 : h2.start))[1], r2.included ? a2 ? r2.render(t2, n2, { end: l2, start: o2 }) : r2.render(t2, n2) : ln(r2, t2, o2, l2)) : r2.render(t2, n2);
}
function gn(e3, t2, s2, i2) {
  const n2 = [];
  let r2, o2, a2, l2, h2 = s2 - 1;
  for (const i3 of e3) {
    for (void 0 !== r2 && (h2 = r2.end + un(t2.original.slice(r2.end, i3.start), ",")), o2 = a2 = h2 + 1 + fn(t2.original.slice(h2 + 1, i3.start))[1]; l2 = t2.original.charCodeAt(o2), 32 === l2 || 9 === l2 || 10 === l2 || 13 === l2; )
      o2++;
    void 0 !== r2 && n2.push({ contentEnd: a2, end: o2, node: r2, separator: h2, start: s2 }), r2 = i3, s2 = o2;
  }
  return n2.push({ contentEnd: i2, end: i2, node: r2, separator: null, start: s2 }), n2;
}
function yn(e3, t2, s2) {
  for (; ; ) {
    const [i2, n2] = fn(e3.original.slice(t2, s2));
    if (-1 === i2)
      break;
    e3.remove(t2 + i2, t2 += n2);
  }
}
class xn extends Ti {
  addDeclaration(e3, t2, s2, i2) {
    if (i2) {
      const n2 = this.parent.addDeclaration(e3, t2, s2, i2);
      return n2.markInitializersForDeoptimization(), n2;
    }
    return super.addDeclaration(e3, t2, s2, false);
  }
}
class En extends Zs {
  initialise() {
    var e3, t2;
    this.directive && "use strict" !== this.directive && this.parent.type === Ls && this.context.log(ve, (e3 = this.directive, { code: "MODULE_LEVEL_DIRECTIVE", id: t2 = this.context.module.id, message: `Module level directives cause errors when bundled, "${e3}" in "${T(t2)}" was ignored.` }), this.start);
  }
  render(e3, t2) {
    super.render(e3, t2), this.included && this.insertSemicolon(e3);
  }
  shouldBeIncluded(e3) {
    return this.directive && "use strict" !== this.directive ? this.parent.type !== Ls : super.shouldBeIncluded(e3);
  }
  applyDeoptimizations() {
  }
}
class bn extends Zs {
  constructor() {
    super(...arguments), this.directlyIncluded = false;
  }
  addImplicitReturnExpressionToScope() {
    const e3 = this.body[this.body.length - 1];
    e3 && "ReturnStatement" === e3.type || this.scope.addReturnExpression(re);
  }
  createScope(e3) {
    this.scope = this.parent.preventChildBlockScope ? e3 : new xn(e3);
  }
  hasEffects(e3) {
    if (this.deoptimizeBody)
      return true;
    for (const t2 of this.body) {
      if (e3.brokenFlow)
        break;
      if (t2.hasEffects(e3))
        return true;
    }
    return false;
  }
  include(e3, t2) {
    if (!this.deoptimizeBody || !this.directlyIncluded) {
      this.included = true, this.directlyIncluded = true, this.deoptimizeBody && (t2 = true);
      for (const s2 of this.body)
        (t2 || s2.shouldBeIncluded(e3)) && s2.include(e3, t2);
    }
  }
  initialise() {
    const e3 = this.body[0];
    this.deoptimizeBody = e3 instanceof En && "use asm" === e3.directive;
  }
  render(e3, t2) {
    this.body.length > 0 ? mn(this.body, e3, this.start + 1, this.end - 1, t2) : super.render(e3, t2);
  }
}
class vn extends Zs {
  constructor() {
    super(...arguments), this.declarationInit = null;
  }
  addExportedVariables(e3, t2) {
    this.argument.addExportedVariables(e3, t2);
  }
  declare(e3, t2) {
    return this.declarationInit = t2, this.argument.declare(e3, re);
  }
  deoptimizePath(e3) {
    0 === e3.length && this.argument.deoptimizePath(K);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return e3.length > 0 || this.argument.hasEffectsOnInteractionAtPath(K, t2, s2);
  }
  markDeclarationReached() {
    this.argument.markDeclarationReached();
  }
  applyDeoptimizations() {
    this.deoptimized = true, null !== this.declarationInit && (this.declarationInit.deoptimizePath([G, G]), this.context.requestTreeshakingPass());
  }
}
class Sn extends Zs {
  constructor() {
    super(...arguments), this.objectEntity = null, this.deoptimizedReturn = false;
  }
  deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2) {
    if (2 === e3.type) {
      const { parameters: t3 } = this.scope, { args: s3 } = e3;
      let i2 = false;
      for (let e4 = 0; e4 < s3.length - 1; e4++) {
        const n2 = this.params[e4], r2 = s3[e4 + 1];
        i2 || n2 instanceof vn ? (i2 = true, r2.deoptimizePath(Y)) : n2 instanceof on ? (t3[e4][0].addEntityToBeDeoptimized(r2), this.addArgumentToBeDeoptimized(r2)) : n2 ? r2.deoptimizePath(Y) : this.addArgumentToBeDeoptimized(r2);
      }
    } else
      this.getObjectEntity().deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2);
  }
  deoptimizePath(e3) {
    this.getObjectEntity().deoptimizePath(e3), 1 === e3.length && e3[0] === G && this.scope.getReturnExpression().deoptimizePath(Y);
  }
  getLiteralValueAtPath(e3, t2, s2) {
    return this.getObjectEntity().getLiteralValueAtPath(e3, t2, s2);
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    return e3.length > 0 ? this.getObjectEntity().getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) : this.async ? (this.deoptimizedReturn || (this.deoptimizedReturn = true, this.scope.getReturnExpression().deoptimizePath(Y), this.context.requestTreeshakingPass()), oe) : [this.scope.getReturnExpression(), false];
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    if (e3.length > 0 || 2 !== t2.type)
      return this.getObjectEntity().hasEffectsOnInteractionAtPath(e3, t2, s2);
    if (this.annotationNoSideEffects)
      return false;
    if (this.async) {
      const { propertyReadSideEffects: e4 } = this.context.options.treeshake, t3 = this.scope.getReturnExpression();
      if (t3.hasEffectsOnInteractionAtPath(["then"], ce, s2) || e4 && ("always" === e4 || t3.hasEffectsOnInteractionAtPath(["then"], le, s2)))
        return true;
    }
    for (const e4 of this.params)
      if (e4.hasEffects(s2))
        return true;
    return false;
  }
  include(e3, t2) {
    this.deoptimized || this.applyDeoptimizations(), this.included = true;
    const { brokenFlow: s2 } = e3;
    e3.brokenFlow = false, this.body.include(e3, t2), e3.brokenFlow = s2;
  }
  includeCallArguments(e3, t2) {
    this.scope.includeCallArguments(e3, t2);
  }
  initialise() {
    this.scope.addParameterVariables(this.params.map((e3) => e3.declare("parameter", re)), this.params[this.params.length - 1] instanceof vn), this.body instanceof bn ? this.body.addImplicitReturnExpressionToScope() : this.scope.addReturnExpression(this.body);
  }
  parseNode(e3) {
    e3.body.type === ks && (this.body = new bn(e3.body, this, this.scope.hoistedBodyVarScope)), super.parseNode(e3);
  }
  addArgumentToBeDeoptimized(e3) {
  }
  applyDeoptimizations() {
  }
}
Sn.prototype.preventChildBlockScope = true;
class An extends Sn {
  constructor() {
    super(...arguments), this.objectEntity = null;
  }
  createScope(e3) {
    this.scope = new Vi(e3, this.context);
  }
  hasEffects() {
    return this.deoptimized || this.applyDeoptimizations(), false;
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    if (super.hasEffectsOnInteractionAtPath(e3, t2, s2))
      return true;
    if (this.annotationNoSideEffects)
      return false;
    if (2 === t2.type) {
      const { ignore: e4, brokenFlow: t3 } = s2;
      if (s2.ignore = { breaks: false, continues: false, labels: /* @__PURE__ */ new Set(), returnYield: true, this: false }, this.body.hasEffects(s2))
        return true;
      s2.ignore = e4, s2.brokenFlow = t3;
    }
    return false;
  }
  include(e3, t2) {
    super.include(e3, t2);
    for (const s2 of this.params)
      s2 instanceof on || s2.include(e3, t2);
  }
  getObjectEntity() {
    return null !== this.objectEntity ? this.objectEntity : this.objectEntity = new oi([], hi);
  }
}
function kn(e3, { exportNamesByVariable: t2, snippets: { _: s2, getObject: i2, getPropertyAccess: n2 } }, r2 = "") {
  if (1 === e3.length && 1 === t2.get(e3[0]).length) {
    const i3 = e3[0];
    return `exports('${t2.get(i3)}',${s2}${i3.getName(n2)}${r2})`;
  }
  {
    const s3 = [];
    for (const i3 of e3)
      for (const e4 of t2.get(i3))
        s3.push([e4, i3.getName(n2) + r2]);
    return `exports(${i2(s3, { lineBreakIndent: null })})`;
  }
}
function In(e3, t2, s2, i2, { exportNamesByVariable: n2, snippets: { _: r2 } }) {
  i2.prependRight(t2, `exports('${n2.get(e3)}',${r2}`), i2.appendLeft(s2, ")");
}
function wn(e3, t2, s2, i2, n2, r2) {
  const { _: o2, getPropertyAccess: a2 } = r2.snippets;
  n2.appendLeft(s2, `,${o2}${kn([e3], r2)},${o2}${e3.getName(a2)}`), i2 && (n2.prependRight(t2, "("), n2.appendLeft(s2, ")"));
}
class Pn extends Zs {
  addExportedVariables(e3, t2) {
    for (const s2 of this.properties)
      "Property" === s2.type ? s2.value.addExportedVariables(e3, t2) : s2.argument.addExportedVariables(e3, t2);
  }
  declare(e3, t2) {
    const s2 = [];
    for (const i2 of this.properties)
      s2.push(...i2.declare(e3, t2));
    return s2;
  }
  deoptimizePath(e3) {
    if (0 === e3.length)
      for (const t2 of this.properties)
        t2.deoptimizePath(e3);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    for (const e4 of this.properties)
      if (e4.hasEffectsOnInteractionAtPath(K, t2, s2))
        return true;
    return false;
  }
  markDeclarationReached() {
    for (const e3 of this.properties)
      e3.markDeclarationReached();
  }
}
class Cn extends Ii {
  constructor(e3) {
    super("arguments", null, re, e3), this.deoptimizedArguments = [];
  }
  addArgumentToBeDeoptimized(e3) {
    this.included ? e3.deoptimizePath(Y) : this.deoptimizedArguments.push(e3);
  }
  hasEffectsOnInteractionAtPath(e3, { type: t2 }) {
    return 0 !== t2 || e3.length > 1;
  }
  include() {
    super.include();
    for (const e3 of this.deoptimizedArguments)
      e3.deoptimizePath(Y);
    this.deoptimizedArguments.length = 0;
  }
}
class $n extends Ni {
  constructor(e3) {
    super("this", null, e3);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return (s2.replacedVariableInits.get(this) || re).hasEffectsOnInteractionAtPath(e3, t2, s2);
  }
}
class Nn extends Vi {
  constructor(e3, t2) {
    super(e3, t2), this.variables.set("arguments", this.argumentsVariable = new Cn(t2)), this.variables.set("this", this.thisVariable = new $n(t2));
  }
  findLexicalBoundary() {
    return this;
  }
  includeCallArguments(e3, t2) {
    if (super.includeCallArguments(e3, t2), this.argumentsVariable.included)
      for (const s2 of t2)
        s2.included || s2.include(e3, false);
  }
}
class _n extends Sn {
  constructor() {
    super(...arguments), this.objectEntity = null;
  }
  createScope(e3) {
    this.scope = new Nn(e3, this.context), this.constructedEntity = new oi(/* @__PURE__ */ Object.create(null), hi), this.scope.thisVariable.addEntityToBeDeoptimized(this.constructedEntity);
  }
  deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2) {
    super.deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2), 2 === e3.type && 0 === t2.length && e3.args[0] && this.scope.thisVariable.addEntityToBeDeoptimized(e3.args[0]);
  }
  hasEffects(e3) {
    var _a2;
    return this.deoptimized || this.applyDeoptimizations(), !this.annotationNoSideEffects && !!((_a2 = this.id) == null ? void 0 : _a2.hasEffects(e3));
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    if (super.hasEffectsOnInteractionAtPath(e3, t2, s2))
      return true;
    if (this.annotationNoSideEffects)
      return false;
    if (2 === t2.type) {
      const e4 = s2.replacedVariableInits.get(this.scope.thisVariable);
      s2.replacedVariableInits.set(this.scope.thisVariable, t2.withNew ? this.constructedEntity : re);
      const { brokenFlow: i2, ignore: n2, replacedVariableInits: r2 } = s2;
      if (s2.ignore = { breaks: false, continues: false, labels: /* @__PURE__ */ new Set(), returnYield: true, this: t2.withNew }, this.body.hasEffects(s2))
        return true;
      s2.brokenFlow = i2, e4 ? r2.set(this.scope.thisVariable, e4) : r2.delete(this.scope.thisVariable), s2.ignore = n2;
    }
    return false;
  }
  include(e3, t2) {
    var _a2;
    super.include(e3, t2), (_a2 = this.id) == null ? void 0 : _a2.include();
    const s2 = this.scope.argumentsVariable.included;
    for (const i2 of this.params)
      i2 instanceof on && !s2 || i2.include(e3, t2);
  }
  initialise() {
    var _a2;
    super.initialise(), (_a2 = this.id) == null ? void 0 : _a2.declare("function", this);
  }
  addArgumentToBeDeoptimized(e3) {
    this.scope.argumentsVariable.addArgumentToBeDeoptimized(e3);
  }
  getObjectEntity() {
    return null !== this.objectEntity ? this.objectEntity : this.objectEntity = new oi([{ key: "prototype", kind: "init", property: new oi([], hi) }], hi);
  }
}
class Rn extends Zs {
  hasEffects() {
    return this.deoptimized || this.applyDeoptimizations(), true;
  }
  include(e3, t2) {
    if (this.deoptimized || this.applyDeoptimizations(), !this.included) {
      this.included = true;
      e:
        if (!this.context.usesTopLevelAwait) {
          let e4 = this.parent;
          do {
            if (e4 instanceof _n || e4 instanceof An)
              break e;
          } while (e4 = e4.parent);
          this.context.usesTopLevelAwait = true;
        }
    }
    this.argument.include(e3, t2);
  }
}
const On = { "!=": (e3, t2) => e3 != t2, "!==": (e3, t2) => e3 !== t2, "%": (e3, t2) => e3 % t2, "&": (e3, t2) => e3 & t2, "*": (e3, t2) => e3 * t2, "**": (e3, t2) => e3 ** t2, "+": (e3, t2) => e3 + t2, "-": (e3, t2) => e3 - t2, "/": (e3, t2) => e3 / t2, "<": (e3, t2) => e3 < t2, "<<": (e3, t2) => e3 << t2, "<=": (e3, t2) => e3 <= t2, "==": (e3, t2) => e3 == t2, "===": (e3, t2) => e3 === t2, ">": (e3, t2) => e3 > t2, ">=": (e3, t2) => e3 >= t2, ">>": (e3, t2) => e3 >> t2, ">>>": (e3, t2) => e3 >>> t2, "^": (e3, t2) => e3 ^ t2, "|": (e3, t2) => e3 | t2 };
function Dn(e3, t2, s2) {
  if (s2.arguments.length > 0)
    if (s2.arguments[s2.arguments.length - 1].included)
      for (const i2 of s2.arguments)
        i2.render(e3, t2);
    else {
      let i2 = s2.arguments.length - 2;
      for (; i2 >= 0 && !s2.arguments[i2].included; )
        i2--;
      if (i2 >= 0) {
        for (let n2 = 0; n2 <= i2; n2++)
          s2.arguments[n2].render(e3, t2);
        e3.remove(un(e3.original, ",", s2.arguments[i2].end), s2.end - 1);
      } else
        e3.remove(un(e3.original, "(", s2.callee.end) + 1, s2.end - 1);
    }
}
class Ln extends Zs {
  deoptimizeArgumentsOnInteractionAtPath() {
  }
  getLiteralValueAtPath(e3) {
    return e3.length > 0 || null === this.value && 110 !== this.context.code.charCodeAt(this.start) || "bigint" == typeof this.value || 47 === this.context.code.charCodeAt(this.start) ? se : this.value;
  }
  getReturnExpressionWhenCalledAtPath(e3) {
    return 1 !== e3.length ? oe : xs(this.members, e3[0]);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    switch (t2.type) {
      case 0:
        return e3.length > (null === this.value ? 0 : 1);
      case 1:
        return true;
      case 2:
        return !!(this.included && this.value instanceof RegExp && (this.value.global || this.value.sticky)) || (1 !== e3.length || ys(this.members, e3[0], t2, s2));
    }
  }
  initialise() {
    this.members = function(e3) {
      if (e3 instanceof RegExp)
        return ms;
      switch (typeof e3) {
        case "boolean":
          return ps;
        case "number":
          return fs;
        case "string":
          return gs;
      }
      return /* @__PURE__ */ Object.create(null);
    }(this.value);
  }
  parseNode(e3) {
    this.value = e3.value, this.regex = e3.regex, super.parseNode(e3);
  }
  render(e3) {
    "string" == typeof this.value && e3.indentExclusionRanges.push([this.start + 1, this.end - 1]);
  }
}
function Tn(e3) {
  return e3.computed ? function(e4) {
    if (e4 instanceof Ln)
      return String(e4.value);
    return null;
  }(e3.property) : e3.property.name;
}
function Mn(e3) {
  const t2 = e3.propertyKey, s2 = e3.object;
  if ("string" == typeof t2) {
    if (s2 instanceof on)
      return [{ key: s2.name, pos: s2.start }, { key: t2, pos: e3.property.start }];
    if (s2 instanceof Vn) {
      const i2 = Mn(s2);
      return i2 && [...i2, { key: t2, pos: e3.property.start }];
    }
  }
  return null;
}
class Vn extends Zs {
  constructor() {
    super(...arguments), this.variable = null, this.assignmentDeoptimized = false, this.bound = false, this.expressionsToBeDeoptimized = [], this.isUndefined = false;
  }
  bind() {
    this.bound = true;
    const e3 = Mn(this), t2 = e3 && this.scope.findVariable(e3[0].key);
    if (t2 == null ? void 0 : t2.isNamespace) {
      const s2 = Bn(t2, e3.slice(1), this.context);
      s2 ? "undefined" === s2 ? this.isUndefined = true : (this.variable = s2, this.scope.addNamespaceMemberAccess(function(e4) {
        let t3 = e4[0].key;
        for (let s3 = 1; s3 < e4.length; s3++)
          t3 += "." + e4[s3].key;
        return t3;
      }(e3), s2)) : super.bind();
    } else
      super.bind();
  }
  deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2) {
    this.variable ? this.variable.deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2) : this.isUndefined || (t2.length < 7 ? this.object.deoptimizeArgumentsOnInteractionAtPath(e3, [this.getPropertyKey(), ...t2], s2) : ae(e3));
  }
  deoptimizeCache() {
    const { expressionsToBeDeoptimized: e3, object: t2 } = this;
    this.expressionsToBeDeoptimized = me, this.propertyKey = G, t2.deoptimizePath(Y);
    for (const t3 of e3)
      t3.deoptimizeCache();
  }
  deoptimizePath(e3) {
    if (0 === e3.length && this.disallowNamespaceReassignment(), this.variable)
      this.variable.deoptimizePath(e3);
    else if (!this.isUndefined && e3.length < 7) {
      const t2 = this.getPropertyKey();
      this.object.deoptimizePath([t2 === G ? W : t2, ...e3]);
    }
  }
  getLiteralValueAtPath(e3, t2, s2) {
    return this.variable ? this.variable.getLiteralValueAtPath(e3, t2, s2) : this.isUndefined ? void 0 : this.propertyKey !== G && e3.length < 7 ? (this.expressionsToBeDeoptimized.push(s2), this.object.getLiteralValueAtPath([this.getPropertyKey(), ...e3], t2, s2)) : se;
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    return this.variable ? this.variable.getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) : this.isUndefined ? [is, false] : this.propertyKey !== G && e3.length < 7 ? (this.expressionsToBeDeoptimized.push(i2), this.object.getReturnExpressionWhenCalledAtPath([this.getPropertyKey(), ...e3], t2, s2, i2)) : oe;
  }
  hasEffects(e3) {
    return this.deoptimized || this.applyDeoptimizations(), this.property.hasEffects(e3) || this.object.hasEffects(e3) || this.hasAccessEffect(e3);
  }
  hasEffectsAsAssignmentTarget(e3, t2) {
    return t2 && !this.deoptimized && this.applyDeoptimizations(), this.assignmentDeoptimized || this.applyAssignmentDeoptimization(), this.property.hasEffects(e3) || this.object.hasEffects(e3) || t2 && this.hasAccessEffect(e3) || this.hasEffectsOnInteractionAtPath(K, this.assignmentInteraction, e3);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return this.variable ? this.variable.hasEffectsOnInteractionAtPath(e3, t2, s2) : !!this.isUndefined || (!(e3.length < 7) || this.object.hasEffectsOnInteractionAtPath([this.getPropertyKey(), ...e3], t2, s2));
  }
  include(e3, t2) {
    this.deoptimized || this.applyDeoptimizations(), this.includeProperties(e3, t2);
  }
  includeAsAssignmentTarget(e3, t2, s2) {
    this.assignmentDeoptimized || this.applyAssignmentDeoptimization(), s2 ? this.include(e3, t2) : this.includeProperties(e3, t2);
  }
  includeCallArguments(e3, t2) {
    this.variable ? this.variable.includeCallArguments(e3, t2) : super.includeCallArguments(e3, t2);
  }
  initialise() {
    this.propertyKey = Tn(this), this.accessInteraction = { args: [this.object], type: 0 };
  }
  isSkippedAsOptional(e3) {
    var _a2, _b;
    return !this.variable && !this.isUndefined && (((_b = (_a2 = this.object).isSkippedAsOptional) == null ? void 0 : _b.call(_a2, e3)) || this.optional && null == this.object.getLiteralValueAtPath(K, ee, e3));
  }
  render(e3, t2, { renderedParentType: s2, isCalleeOfRenderedParent: i2, renderedSurroundingElement: n2 } = pe) {
    if (this.variable || this.isUndefined) {
      const { snippets: { getPropertyAccess: n3 } } = t2;
      let r2 = this.variable ? this.variable.getName(n3) : "undefined";
      s2 && i2 && (r2 = "0, " + r2), e3.overwrite(this.start, this.end, r2, { contentOnly: true, storeName: true });
    } else
      s2 && i2 && e3.appendRight(this.start, "0, "), this.object.render(e3, t2, { renderedSurroundingElement: n2 }), this.property.render(e3, t2);
  }
  setAssignedValue(e3) {
    this.assignmentInteraction = { args: [this.object, e3], type: 1 };
  }
  applyDeoptimizations() {
    this.deoptimized = true;
    const { propertyReadSideEffects: e3 } = this.context.options.treeshake;
    if (this.bound && e3 && !this.variable && !this.isUndefined) {
      const e4 = this.getPropertyKey();
      this.object.deoptimizeArgumentsOnInteractionAtPath(this.accessInteraction, [e4], ee), this.context.requestTreeshakingPass();
    }
  }
  applyAssignmentDeoptimization() {
    this.assignmentDeoptimized = true;
    const { propertyReadSideEffects: e3 } = this.context.options.treeshake;
    this.bound && e3 && !this.variable && !this.isUndefined && (this.object.deoptimizeArgumentsOnInteractionAtPath(this.assignmentInteraction, [this.getPropertyKey()], ee), this.context.requestTreeshakingPass());
  }
  disallowNamespaceReassignment() {
    if (this.object instanceof on) {
      this.scope.findVariable(this.object.name).isNamespace && (this.variable && this.context.includeVariableInModule(this.variable), this.context.log(ve, Tt(this.object.name, this.context.module.id), this.start));
    }
  }
  getPropertyKey() {
    if (null === this.propertyKey) {
      this.propertyKey = G;
      const e3 = this.property.getLiteralValueAtPath(K, ee, this);
      return this.propertyKey = e3 === H ? e3 : "symbol" == typeof e3 ? G : String(e3);
    }
    return this.propertyKey;
  }
  hasAccessEffect(e3) {
    const { propertyReadSideEffects: t2 } = this.context.options.treeshake;
    return !(this.variable || this.isUndefined) && t2 && ("always" === t2 || this.object.hasEffectsOnInteractionAtPath([this.getPropertyKey()], this.accessInteraction, e3));
  }
  includeProperties(e3, t2) {
    this.included || (this.included = true, this.variable && this.context.includeVariableInModule(this.variable)), this.object.include(e3, t2), this.property.include(e3, t2);
  }
}
function Bn(e3, t2, s2) {
  if (0 === t2.length)
    return e3;
  if (!e3.isNamespace || e3 instanceof de)
    return null;
  const i2 = t2[0].key, n2 = e3.context.traceExport(i2);
  if (!n2) {
    if (1 === t2.length) {
      const n3 = e3.context.fileName;
      return s2.log(ve, Ft(i2, s2.module.id, n3), t2[0].pos), "undefined";
    }
    return null;
  }
  return Bn(n2, t2.slice(1), s2);
}
class zn extends Zs {
  constructor() {
    super(...arguments), this.returnExpression = null, this.deoptimizableDependentExpressions = [], this.expressionsToBeDeoptimized = /* @__PURE__ */ new Set();
  }
  deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2) {
    const { args: i2 } = e3, [n2, r2] = this.getReturnExpression(s2);
    if (r2)
      return;
    const o2 = i2.filter((e4) => !!e4 && e4 !== re);
    if (0 !== o2.length)
      if (n2 === re)
        for (const e4 of o2)
          e4.deoptimizePath(Y);
      else
        s2.withTrackedEntityAtPath(t2, n2, () => {
          for (const e4 of o2)
            this.expressionsToBeDeoptimized.add(e4);
          n2.deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2);
        }, null);
  }
  deoptimizeCache() {
    var _a2;
    if (((_a2 = this.returnExpression) == null ? void 0 : _a2[0]) !== re) {
      this.returnExpression = oe;
      const { deoptimizableDependentExpressions: e3, expressionsToBeDeoptimized: t2 } = this;
      this.expressionsToBeDeoptimized = ge, this.deoptimizableDependentExpressions = me;
      for (const t3 of e3)
        t3.deoptimizeCache();
      for (const e4 of t2)
        e4.deoptimizePath(Y);
    }
  }
  deoptimizePath(e3) {
    if (0 === e3.length || this.context.deoptimizationTracker.trackEntityAtPathAndGetIfTracked(e3, this))
      return;
    const [t2] = this.getReturnExpression();
    t2 !== re && t2.deoptimizePath(e3);
  }
  getLiteralValueAtPath(e3, t2, s2) {
    const [i2] = this.getReturnExpression(t2);
    return i2 === re ? se : t2.withTrackedEntityAtPath(e3, i2, () => (this.deoptimizableDependentExpressions.push(s2), i2.getLiteralValueAtPath(e3, t2, s2)), se);
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    const n2 = this.getReturnExpression(s2);
    return n2[0] === re ? n2 : s2.withTrackedEntityAtPath(e3, n2, () => {
      this.deoptimizableDependentExpressions.push(i2);
      const [r2, o2] = n2[0].getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2);
      return [r2, o2 || n2[1]];
    }, oe);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    const { type: i2 } = t2;
    if (2 === i2) {
      const { args: i3, withNew: n3 } = t2;
      if ((n3 ? s2.instantiated : s2.called).trackEntityAtPathAndGetIfTracked(e3, i3, this))
        return false;
    } else if ((1 === i2 ? s2.assigned : s2.accessed).trackEntityAtPathAndGetIfTracked(e3, this))
      return false;
    const [n2, r2] = this.getReturnExpression();
    return (1 === i2 || !r2) && n2.hasEffectsOnInteractionAtPath(e3, t2, s2);
  }
}
class Fn extends zn {
  bind() {
    if (super.bind(), this.callee instanceof on) {
      this.scope.findVariable(this.callee.name).isNamespace && this.context.log(ve, Rt(this.callee.name), this.start), "eval" === this.callee.name && this.context.log(ve, { code: "EVAL", id: e3 = this.context.module.id, message: `Use of eval in "${T(e3)}" is strongly discouraged as it poses security risks and may cause issues with minification.`, url: Oe("troubleshooting/#avoiding-eval") }, this.start);
    }
    var e3;
    this.interaction = { args: [this.callee instanceof Vn && !this.callee.variable ? this.callee.object : null, ...this.arguments], type: 2, withNew: false };
  }
  hasEffects(e3) {
    try {
      for (const t2 of this.arguments)
        if (t2.hasEffects(e3))
          return true;
      return !this.annotationPure && (this.callee.hasEffects(e3) || this.callee.hasEffectsOnInteractionAtPath(K, this.interaction, e3));
    } finally {
      this.deoptimized || this.applyDeoptimizations();
    }
  }
  include(e3, t2) {
    this.deoptimized || this.applyDeoptimizations(), t2 ? (super.include(e3, t2), t2 === Qs && this.callee instanceof on && this.callee.variable && this.callee.variable.markCalledFromTryStatement()) : (this.included = true, this.callee.include(e3, false)), this.callee.includeCallArguments(e3, this.arguments);
  }
  isSkippedAsOptional(e3) {
    var _a2, _b;
    return ((_b = (_a2 = this.callee).isSkippedAsOptional) == null ? void 0 : _b.call(_a2, e3)) || this.optional && null == this.callee.getLiteralValueAtPath(K, ee, e3);
  }
  render(e3, t2, { renderedSurroundingElement: s2 } = pe) {
    this.callee.render(e3, t2, { isCalleeOfRenderedParent: true, renderedSurroundingElement: s2 }), Dn(e3, t2, this);
  }
  applyDeoptimizations() {
    this.deoptimized = true, this.callee.deoptimizeArgumentsOnInteractionAtPath(this.interaction, K, ee), this.context.requestTreeshakingPass();
  }
  getReturnExpression(e3 = ee) {
    return null === this.returnExpression ? (this.returnExpression = oe, this.returnExpression = this.callee.getReturnExpressionWhenCalledAtPath(K, this.interaction, e3, this)) : this.returnExpression;
  }
}
class jn extends Mi {
  addDeclaration(e3, t2, s2, i2) {
    const n2 = this.variables.get(e3.name);
    return n2 ? (this.parent.addDeclaration(e3, t2, is, i2), n2.addDeclaration(e3, s2), n2) : this.parent.addDeclaration(e3, t2, s2, i2);
  }
}
class Un extends Ti {
  constructor(e3, t2, s2) {
    super(e3), this.variables.set("this", this.thisVariable = new Ii("this", null, t2, s2)), this.instanceScope = new Ti(this), this.instanceScope.variables.set("this", new $n(s2));
  }
  findLexicalBoundary() {
    return this;
  }
}
class Gn extends Zs {
  constructor() {
    super(...arguments), this.accessedValue = null;
  }
  deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2) {
    return 0 === e3.type && "get" === this.kind && 0 === t2.length || 1 === e3.type && "set" === this.kind && 0 === t2.length ? this.value.deoptimizeArgumentsOnInteractionAtPath({ args: e3.args, type: 2, withNew: false }, K, s2) : void this.getAccessedValue()[0].deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2);
  }
  deoptimizeCache() {
  }
  deoptimizePath(e3) {
    this.getAccessedValue()[0].deoptimizePath(e3);
  }
  getLiteralValueAtPath(e3, t2, s2) {
    return this.getAccessedValue()[0].getLiteralValueAtPath(e3, t2, s2);
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    return this.getAccessedValue()[0].getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2);
  }
  hasEffects(e3) {
    return this.key.hasEffects(e3);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return "get" === this.kind && 0 === t2.type && 0 === e3.length || "set" === this.kind && 1 === t2.type ? this.value.hasEffectsOnInteractionAtPath(K, { args: t2.args, type: 2, withNew: false }, s2) : this.getAccessedValue()[0].hasEffectsOnInteractionAtPath(e3, t2, s2);
  }
  applyDeoptimizations() {
  }
  getAccessedValue() {
    return null === this.accessedValue ? "get" === this.kind ? (this.accessedValue = oe, this.accessedValue = this.value.getReturnExpressionWhenCalledAtPath(K, ce, ee, this)) : this.accessedValue = [this.value, false] : this.accessedValue;
  }
}
class Wn extends Gn {
  applyDeoptimizations() {
  }
}
class qn extends ne {
  constructor(e3, t2) {
    super(), this.object = e3, this.key = t2;
  }
  deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2) {
    this.object.deoptimizeArgumentsOnInteractionAtPath(e3, [this.key, ...t2], s2);
  }
  deoptimizePath(e3) {
    this.object.deoptimizePath([this.key, ...e3]);
  }
  getLiteralValueAtPath(e3, t2, s2) {
    return this.object.getLiteralValueAtPath([this.key, ...e3], t2, s2);
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    return this.object.getReturnExpressionWhenCalledAtPath([this.key, ...e3], t2, s2, i2);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return this.object.hasEffectsOnInteractionAtPath([this.key, ...e3], t2, s2);
  }
}
class Hn extends Zs {
  constructor() {
    super(...arguments), this.objectEntity = null;
  }
  createScope(e3) {
    this.scope = new Ti(e3);
  }
  deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2) {
    this.getObjectEntity().deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2);
  }
  deoptimizeCache() {
    this.getObjectEntity().deoptimizeAllProperties();
  }
  deoptimizePath(e3) {
    this.getObjectEntity().deoptimizePath(e3);
  }
  getLiteralValueAtPath(e3, t2, s2) {
    return this.getObjectEntity().getLiteralValueAtPath(e3, t2, s2);
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    return this.getObjectEntity().getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2);
  }
  hasEffects(e3) {
    var _a2, _b;
    this.deoptimized || this.applyDeoptimizations();
    const t2 = ((_a2 = this.superClass) == null ? void 0 : _a2.hasEffects(e3)) || this.body.hasEffects(e3);
    return (_b = this.id) == null ? void 0 : _b.markDeclarationReached(), t2 || super.hasEffects(e3);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    var _a2;
    return 2 === t2.type && 0 === e3.length ? !t2.withNew || (null === this.classConstructor ? (_a2 = this.superClass) == null ? void 0 : _a2.hasEffectsOnInteractionAtPath(e3, t2, s2) : this.classConstructor.hasEffectsOnInteractionAtPath(e3, t2, s2)) || false : this.getObjectEntity().hasEffectsOnInteractionAtPath(e3, t2, s2);
  }
  include(e3, t2) {
    var _a2;
    this.deoptimized || this.applyDeoptimizations(), this.included = true, (_a2 = this.superClass) == null ? void 0 : _a2.include(e3, t2), this.body.include(e3, t2), this.id && (this.id.markDeclarationReached(), this.id.include());
  }
  initialise() {
    var _a2;
    (_a2 = this.id) == null ? void 0 : _a2.declare("class", this);
    for (const e3 of this.body.body)
      if (e3 instanceof Wn && "constructor" === e3.kind)
        return void (this.classConstructor = e3);
    this.classConstructor = null;
  }
  applyDeoptimizations() {
    this.deoptimized = true;
    for (const e3 of this.body.body)
      e3.static || e3 instanceof Wn && "constructor" === e3.kind || e3.deoptimizePath(Y);
    this.context.requestTreeshakingPass();
  }
  getObjectEntity() {
    if (null !== this.objectEntity)
      return this.objectEntity;
    const e3 = [], t2 = [];
    for (const s2 of this.body.body) {
      const i2 = s2.static ? e3 : t2, n2 = s2.kind;
      if (i2 === t2 && !n2)
        continue;
      const r2 = "set" === n2 || "get" === n2 ? n2 : "init";
      let o2;
      if (s2.computed) {
        const e4 = s2.key.getLiteralValueAtPath(K, ee, this);
        if ("symbol" == typeof e4) {
          i2.push({ key: G, kind: r2, property: s2 });
          continue;
        }
        o2 = String(e4);
      } else
        o2 = s2.key instanceof on ? s2.key.name : String(s2.key.value);
      i2.push({ key: o2, kind: r2, property: s2 });
    }
    return e3.unshift({ key: "prototype", kind: "init", property: new oi(t2, this.superClass ? new qn(this.superClass, "prototype") : hi) }), this.objectEntity = new oi(e3, this.superClass || hi);
  }
}
class Kn extends Hn {
  initialise() {
    super.initialise(), null !== this.id && (this.id.variable.isId = true);
  }
  parseNode(e3) {
    null !== e3.id && (this.id = new on(e3.id, this, this.scope.parent)), super.parseNode(e3);
  }
  render(e3, t2) {
    var _a2;
    const { exportNamesByVariable: s2, format: i2, snippets: { _: n2, getPropertyAccess: r2 } } = t2;
    if (this.id) {
      const { variable: o2, name: a2 } = this.id;
      "system" === i2 && s2.has(o2) && e3.appendLeft(this.end, `${n2}${kn([o2], t2)};`);
      const l2 = o2.getName(r2);
      if (l2 !== a2)
        return (_a2 = this.superClass) == null ? void 0 : _a2.render(e3, t2), this.body.render(e3, { ...t2, useOriginalName: (e4) => e4 === o2 }), e3.prependRight(this.start, `let ${l2}${n2}=${n2}`), void e3.prependLeft(this.end, ";");
    }
    super.render(e3, t2);
  }
  applyDeoptimizations() {
    super.applyDeoptimizations();
    const { id: e3, scope: t2 } = this;
    if (e3) {
      const { name: s2, variable: i2 } = e3;
      for (const e4 of t2.accessedOutsideVariables.values())
        e4 !== i2 && e4.forbidName(s2);
    }
  }
}
class Yn extends Hn {
  render(e3, t2, { renderedSurroundingElement: s2 } = pe) {
    super.render(e3, t2), s2 === Ns && (e3.appendRight(this.start, "("), e3.prependLeft(this.end, ")"));
  }
}
class Xn extends ne {
  constructor(e3) {
    super(), this.expressions = e3, this.included = false;
  }
  deoptimizePath(e3) {
    for (const t2 of this.expressions)
      t2.deoptimizePath(e3);
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    return [new Xn(this.expressions.map((n2) => n2.getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2)[0])), false];
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    for (const i2 of this.expressions)
      if (i2.hasEffectsOnInteractionAtPath(e3, t2, s2))
        return true;
    return false;
  }
}
function Qn(e3, t2) {
  const { brokenFlow: s2, hasBreak: i2, hasContinue: n2, ignore: r2 } = e3, { breaks: o2, continues: a2 } = r2;
  return r2.breaks = true, r2.continues = true, e3.hasBreak = false, e3.hasContinue = false, !!t2.hasEffects(e3) || (r2.breaks = o2, r2.continues = a2, e3.hasBreak = i2, e3.hasContinue = n2, e3.brokenFlow = s2, false);
}
function Zn(e3, t2, s2) {
  const { brokenFlow: i2, hasBreak: n2, hasContinue: r2 } = e3;
  e3.hasBreak = false, e3.hasContinue = false, t2.include(e3, s2, { asSingleStatement: true }), e3.hasBreak = n2, e3.hasContinue = r2, e3.brokenFlow = i2;
}
class Jn extends Zs {
  hasEffects() {
    return false;
  }
  initialise() {
    this.context.addExport(this);
  }
  render(e3, t2, s2) {
    e3.remove(s2.start, s2.end);
  }
  applyDeoptimizations() {
  }
}
Jn.prototype.needsBoundaries = true;
class er extends _n {
  initialise() {
    super.initialise(), null !== this.id && (this.id.variable.isId = true);
  }
  parseNode(e3) {
    null !== e3.id && (this.id = new on(e3.id, this, this.scope.parent)), super.parseNode(e3);
  }
}
class tr extends Zs {
  include(e3, t2) {
    super.include(e3, t2), t2 && this.context.includeVariableInModule(this.variable);
  }
  initialise() {
    const e3 = this.declaration;
    this.declarationName = e3.id && e3.id.name || this.declaration.name, this.variable = this.scope.addExportDefaultDeclaration(this.declarationName || this.context.getModuleName(), this, this.context), this.context.addExport(this);
  }
  render(e3, t2, s2) {
    const { start: i2, end: n2 } = s2, r2 = function(e4, t3) {
      return pn(e4, un(e4, "default", t3) + 7);
    }(e3.original, this.start);
    if (this.declaration instanceof er)
      this.renderNamedDeclaration(e3, r2, null === this.declaration.id ? function(e4, t3) {
        const s3 = un(e4, "function", t3) + 8;
        e4 = e4.slice(s3, un(e4, "(", s3));
        const i3 = un(e4, "*");
        return -1 === i3 ? s3 : s3 + i3 + 1;
      }(e3.original, r2) : null, t2);
    else if (this.declaration instanceof Kn)
      this.renderNamedDeclaration(e3, r2, null === this.declaration.id ? un(e3.original, "class", i2) + 5 : null, t2);
    else {
      if (this.variable.getOriginalVariable() !== this.variable)
        return void ln(this, e3, i2, n2);
      if (!this.variable.included)
        return e3.remove(this.start, r2), this.declaration.render(e3, t2, { renderedSurroundingElement: Ns }), void (";" !== e3.original[this.end - 1] && e3.appendLeft(this.end, ";"));
      this.renderVariableDeclaration(e3, r2, t2);
    }
    this.declaration.render(e3, t2);
  }
  applyDeoptimizations() {
  }
  renderNamedDeclaration(e3, t2, s2, i2) {
    const { exportNamesByVariable: n2, format: r2, snippets: { getPropertyAccess: o2 } } = i2, a2 = this.variable.getName(o2);
    e3.remove(this.start, t2), null !== s2 && e3.appendLeft(s2, ` ${a2}`), "system" === r2 && this.declaration instanceof Kn && n2.has(this.variable) && e3.appendLeft(this.end, ` ${kn([this.variable], i2)};`);
  }
  renderVariableDeclaration(e3, t2, { format: s2, exportNamesByVariable: i2, snippets: { cnst: n2, getPropertyAccess: r2 } }) {
    const o2 = 59 === e3.original.charCodeAt(this.end - 1), a2 = "system" === s2 && i2.get(this.variable);
    a2 ? (e3.overwrite(this.start, t2, `${n2} ${this.variable.getName(r2)} = exports('${a2[0]}', `), e3.appendRight(o2 ? this.end - 1 : this.end, ")" + (o2 ? "" : ";"))) : (e3.overwrite(this.start, t2, `${n2} ${this.variable.getName(r2)} = `), o2 || e3.appendLeft(this.end, ";"));
  }
}
tr.prototype.needsBoundaries = true;
class sr extends Zs {
  bind() {
    var _a2;
    (_a2 = this.declaration) == null ? void 0 : _a2.bind();
  }
  hasEffects(e3) {
    var _a2;
    return !!((_a2 = this.declaration) == null ? void 0 : _a2.hasEffects(e3));
  }
  initialise() {
    this.context.addExport(this);
  }
  render(e3, t2, s2) {
    const { start: i2, end: n2 } = s2;
    null === this.declaration ? e3.remove(i2, n2) : (e3.remove(this.start, this.declaration.start), this.declaration.render(e3, t2, { end: n2, start: i2 }));
  }
  applyDeoptimizations() {
  }
}
sr.prototype.needsBoundaries = true;
class ir extends _n {
  render(e3, t2, { renderedSurroundingElement: s2 } = pe) {
    super.render(e3, t2), s2 === Ns && (e3.appendRight(this.start, "("), e3.prependLeft(this.end, ")"));
  }
}
class nr extends xn {
  constructor() {
    super(...arguments), this.hoistedDeclarations = [];
  }
  addDeclaration(e3, t2, s2, i2) {
    return this.hoistedDeclarations.push(e3), super.addDeclaration(e3, t2, s2, i2);
  }
}
const rr = Symbol("unset");
class or extends Zs {
  constructor() {
    super(...arguments), this.testValue = rr;
  }
  deoptimizeCache() {
    this.testValue = se;
  }
  hasEffects(e3) {
    var _a2;
    if (this.test.hasEffects(e3))
      return true;
    const t2 = this.getTestValue();
    if ("symbol" == typeof t2) {
      const { brokenFlow: t3 } = e3;
      if (this.consequent.hasEffects(e3))
        return true;
      const s2 = e3.brokenFlow;
      return e3.brokenFlow = t3, null === this.alternate ? false : !!this.alternate.hasEffects(e3) || (e3.brokenFlow = e3.brokenFlow && s2, false);
    }
    return t2 ? this.consequent.hasEffects(e3) : !!((_a2 = this.alternate) == null ? void 0 : _a2.hasEffects(e3));
  }
  include(e3, t2) {
    if (this.included = true, t2)
      this.includeRecursively(t2, e3);
    else {
      const t3 = this.getTestValue();
      "symbol" == typeof t3 ? this.includeUnknownTest(e3) : this.includeKnownTest(e3, t3);
    }
  }
  parseNode(e3) {
    this.consequentScope = new nr(this.scope), this.consequent = new (this.context.getNodeConstructor(e3.consequent.type))(e3.consequent, this, this.consequentScope), e3.alternate && (this.alternateScope = new nr(this.scope), this.alternate = new (this.context.getNodeConstructor(e3.alternate.type))(e3.alternate, this, this.alternateScope)), super.parseNode(e3);
  }
  render(e3, t2) {
    const { snippets: { getPropertyAccess: s2 } } = t2, i2 = this.getTestValue(), n2 = [], r2 = this.test.included, o2 = !this.context.options.treeshake;
    r2 ? this.test.render(e3, t2) : e3.remove(this.start, this.consequent.start), this.consequent.included && (o2 || "symbol" == typeof i2 || i2) ? this.consequent.render(e3, t2) : (e3.overwrite(this.consequent.start, this.consequent.end, r2 ? ";" : ""), n2.push(...this.consequentScope.hoistedDeclarations)), this.alternate && (!this.alternate.included || !o2 && "symbol" != typeof i2 && i2 ? (r2 && this.shouldKeepAlternateBranch() ? e3.overwrite(this.alternate.start, this.end, ";") : e3.remove(this.consequent.end, this.end), n2.push(...this.alternateScope.hoistedDeclarations)) : (r2 ? 101 === e3.original.charCodeAt(this.alternate.start - 1) && e3.prependLeft(this.alternate.start, " ") : e3.remove(this.consequent.end, this.alternate.start), this.alternate.render(e3, t2))), this.renderHoistedDeclarations(n2, e3, s2);
  }
  applyDeoptimizations() {
  }
  getTestValue() {
    return this.testValue === rr ? this.testValue = this.test.getLiteralValueAtPath(K, ee, this) : this.testValue;
  }
  includeKnownTest(e3, t2) {
    var _a2;
    this.test.shouldBeIncluded(e3) && this.test.include(e3, false), t2 && this.consequent.shouldBeIncluded(e3) && this.consequent.include(e3, false, { asSingleStatement: true }), !t2 && ((_a2 = this.alternate) == null ? void 0 : _a2.shouldBeIncluded(e3)) && this.alternate.include(e3, false, { asSingleStatement: true });
  }
  includeRecursively(e3, t2) {
    var _a2;
    this.test.include(t2, e3), this.consequent.include(t2, e3), (_a2 = this.alternate) == null ? void 0 : _a2.include(t2, e3);
  }
  includeUnknownTest(e3) {
    var _a2;
    this.test.include(e3, false);
    const { brokenFlow: t2 } = e3;
    let s2 = false;
    this.consequent.shouldBeIncluded(e3) && (this.consequent.include(e3, false, { asSingleStatement: true }), s2 = e3.brokenFlow, e3.brokenFlow = t2), ((_a2 = this.alternate) == null ? void 0 : _a2.shouldBeIncluded(e3)) && (this.alternate.include(e3, false, { asSingleStatement: true }), e3.brokenFlow = e3.brokenFlow && s2);
  }
  renderHoistedDeclarations(e3, t2, s2) {
    const i2 = [...new Set(e3.map((e4) => {
      const t3 = e4.variable;
      return t3.included ? t3.getName(s2) : "";
    }))].filter(Boolean).join(", ");
    if (i2) {
      const e4 = this.parent.type, s3 = e4 !== Ls && e4 !== ks;
      t2.prependRight(this.start, `${s3 ? "{ " : ""}var ${i2}; `), s3 && t2.appendLeft(this.end, " }");
    }
  }
  shouldKeepAlternateBranch() {
    let e3 = this.parent;
    do {
      if (e3 instanceof or && e3.alternate)
        return true;
      if (e3 instanceof bn)
        return false;
      e3 = e3.parent;
    } while (e3);
    return false;
  }
}
class ar extends Zs {
  bind() {
  }
  hasEffects() {
    return false;
  }
  initialise() {
    this.context.addImport(this);
  }
  render(e3, t2, s2) {
    e3.remove(s2.start, s2.end);
  }
  applyDeoptimizations() {
  }
}
ar.prototype.needsBoundaries = true;
class lr extends Zs {
  applyDeoptimizations() {
  }
}
const hr = "_interopDefault", cr = "_interopDefaultCompat", ur = "_interopNamespace", dr = "_interopNamespaceCompat", pr = "_interopNamespaceDefault", fr = "_interopNamespaceDefaultOnly", mr = "_mergeNamespaces", gr = { auto: hr, compat: cr, default: null, defaultOnly: null, esModule: null }, yr = (e3, t2) => "esModule" === e3 || t2 && ("auto" === e3 || "compat" === e3), xr = { auto: ur, compat: dr, default: pr, defaultOnly: fr, esModule: null }, Er = (e3, t2) => "esModule" !== e3 && yr(e3, t2), br = (e3, t2, s2, i2, n2, r2, o2) => {
  const a2 = new Set(e3);
  for (const e4 of Dr)
    t2.has(e4) && a2.add(e4);
  return Dr.map((e4) => a2.has(e4) ? vr[e4](s2, i2, n2, r2, o2, a2) : "").join("");
}, vr = { [cr](e3, t2, s2) {
  const { _: i2, getDirectReturnFunction: n2, n: r2 } = t2, [o2, a2] = n2(["e"], { functionReturn: true, lineBreakIndent: null, name: cr });
  return `${o2}${kr(t2)}${i2}?${i2}${s2 ? Sr(t2) : Ar(t2)}${a2}${r2}${r2}`;
}, [hr](e3, t2, s2) {
  const { _: i2, getDirectReturnFunction: n2, n: r2 } = t2, [o2, a2] = n2(["e"], { functionReturn: true, lineBreakIndent: null, name: hr });
  return `${o2}e${i2}&&${i2}e.__esModule${i2}?${i2}${s2 ? Sr(t2) : Ar(t2)}${a2}${r2}${r2}`;
}, [dr](e3, t2, s2, i2, n2, r2) {
  const { _: o2, getDirectReturnFunction: a2, n: l2 } = t2;
  if (r2.has(pr)) {
    const [e4, s3] = a2(["e"], { functionReturn: true, lineBreakIndent: null, name: dr });
    return `${e4}${kr(t2)}${o2}?${o2}e${o2}:${o2}${pr}(e)${s3}${l2}${l2}`;
  }
  return `function ${dr}(e)${o2}{${l2}${e3}if${o2}(${kr(t2)})${o2}return e;${l2}` + Ir(e3, e3, t2, s2, i2, n2) + `}${l2}${l2}`;
}, [fr](e3, t2, s2, i2, n2) {
  const { getDirectReturnFunction: r2, getObject: o2, n: a2 } = t2, [l2, h2] = r2(["e"], { functionReturn: true, lineBreakIndent: null, name: fr });
  return `${l2}${Rr(i2, Or(n2, o2([["__proto__", "null"], ["default", "e"]], { lineBreakIndent: null }), t2))}${h2}${a2}${a2}`;
}, [pr](e3, t2, s2, i2, n2) {
  const { _: r2, n: o2 } = t2;
  return `function ${pr}(e)${r2}{${o2}` + Ir(e3, e3, t2, s2, i2, n2) + `}${o2}${o2}`;
}, [ur](e3, t2, s2, i2, n2, r2) {
  const { _: o2, getDirectReturnFunction: a2, n: l2 } = t2;
  if (r2.has(pr)) {
    const [e4, t3] = a2(["e"], { functionReturn: true, lineBreakIndent: null, name: ur });
    return `${e4}e${o2}&&${o2}e.__esModule${o2}?${o2}e${o2}:${o2}${pr}(e)${t3}${l2}${l2}`;
  }
  return `function ${ur}(e)${o2}{${l2}${e3}if${o2}(e${o2}&&${o2}e.__esModule)${o2}return e;${l2}` + Ir(e3, e3, t2, s2, i2, n2) + `}${l2}${l2}`;
}, [mr](e3, t2, s2, i2, n2) {
  const { _: r2, cnst: o2, n: a2 } = t2, l2 = "var" === o2 && s2;
  return `function ${mr}(n, m)${r2}{${a2}${e3}${Pr(`{${a2}${e3}${e3}${e3}if${r2}(k${r2}!==${r2}'default'${r2}&&${r2}!(k in n))${r2}{${a2}` + (s2 ? l2 ? $r : Nr : _r)(e3, e3 + e3 + e3 + e3, t2) + `${e3}${e3}${e3}}${a2}${e3}${e3}}`, l2, e3, t2)}${a2}${e3}return ${Rr(i2, Or(n2, "n", t2))};${a2}}${a2}${a2}`;
} }, Sr = ({ _: e3, getObject: t2 }) => `e${e3}:${e3}${t2([["default", "e"]], { lineBreakIndent: null })}`, Ar = ({ _: e3, getPropertyAccess: t2 }) => `e${t2("default")}${e3}:${e3}e`, kr = ({ _: e3 }) => `e${e3}&&${e3}typeof e${e3}===${e3}'object'${e3}&&${e3}'default'${e3}in e`, Ir = (e3, t2, s2, i2, n2, r2) => {
  const { _: o2, cnst: a2, getObject: l2, getPropertyAccess: h2, n: c2, s: u2 } = s2, d2 = `{${c2}` + (i2 ? Cr : _r)(e3, t2 + e3 + e3, s2) + `${t2}${e3}}`;
  return `${t2}${a2} n${o2}=${o2}Object.create(null${r2 ? `,${o2}{${o2}[Symbol.toStringTag]:${o2}${Lr(l2)}${o2}}` : ""});${c2}${t2}if${o2}(e)${o2}{${c2}${t2}${e3}${wr(d2, !i2, s2)}${c2}${t2}}${c2}${t2}n${h2("default")}${o2}=${o2}e;${c2}${t2}return ${Rr(n2, "n")}${u2}${c2}`;
}, wr = (e3, t2, { _: s2, cnst: i2, getFunctionIntro: n2, s: r2 }) => "var" !== i2 || t2 ? `for${s2}(${i2} k in e)${s2}${e3}` : `Object.keys(e).forEach(${n2(["k"], { isAsync: false, name: null })}${e3})${r2}`, Pr = (e3, t2, s2, { _: i2, cnst: n2, getDirectReturnFunction: r2, getFunctionIntro: o2, n: a2 }) => {
  if (t2) {
    const [t3, n3] = r2(["e"], { functionReturn: false, lineBreakIndent: { base: s2, t: s2 }, name: null });
    return `m.forEach(${t3}e${i2}&&${i2}typeof e${i2}!==${i2}'string'${i2}&&${i2}!Array.isArray(e)${i2}&&${i2}Object.keys(e).forEach(${o2(["k"], { isAsync: false, name: null })}${e3})${n3});`;
  }
  return `for${i2}(var i${i2}=${i2}0;${i2}i${i2}<${i2}m.length;${i2}i++)${i2}{${a2}${s2}${s2}${n2} e${i2}=${i2}m[i];${a2}${s2}${s2}if${i2}(typeof e${i2}!==${i2}'string'${i2}&&${i2}!Array.isArray(e))${i2}{${i2}for${i2}(${n2} k in e)${i2}${e3}${i2}}${a2}${s2}}`;
}, Cr = (e3, t2, s2) => {
  const { _: i2, n: n2 } = s2;
  return `${t2}if${i2}(k${i2}!==${i2}'default')${i2}{${n2}` + $r(e3, t2 + e3, s2) + `${t2}}${n2}`;
}, $r = (e3, t2, { _: s2, cnst: i2, getDirectReturnFunction: n2, n: r2 }) => {
  const [o2, a2] = n2([], { functionReturn: true, lineBreakIndent: null, name: null });
  return `${t2}${i2} d${s2}=${s2}Object.getOwnPropertyDescriptor(e,${s2}k);${r2}${t2}Object.defineProperty(n,${s2}k,${s2}d.get${s2}?${s2}d${s2}:${s2}{${r2}${t2}${e3}enumerable:${s2}true,${r2}${t2}${e3}get:${s2}${o2}e[k]${a2}${r2}${t2}});${r2}`;
}, Nr = (e3, t2, { _: s2, cnst: i2, getDirectReturnFunction: n2, n: r2 }) => {
  const [o2, a2] = n2([], { functionReturn: true, lineBreakIndent: null, name: null });
  return `${t2}${i2} d${s2}=${s2}Object.getOwnPropertyDescriptor(e,${s2}k);${r2}${t2}if${s2}(d)${s2}{${r2}${t2}${e3}Object.defineProperty(n,${s2}k,${s2}d.get${s2}?${s2}d${s2}:${s2}{${r2}${t2}${e3}${e3}enumerable:${s2}true,${r2}${t2}${e3}${e3}get:${s2}${o2}e[k]${a2}${r2}${t2}${e3}});${r2}${t2}}${r2}`;
}, _r = (e3, t2, { _: s2, n: i2 }) => `${t2}n[k]${s2}=${s2}e[k];${i2}`, Rr = (e3, t2) => e3 ? `Object.freeze(${t2})` : t2, Or = (e3, t2, { _: s2, getObject: i2 }) => e3 ? `Object.defineProperty(${t2},${s2}Symbol.toStringTag,${s2}${Lr(i2)})` : t2, Dr = Object.keys(vr);
function Lr(e3) {
  return e3([["value", "'Module'"]], { lineBreakIndent: null });
}
function Tr(e3, t2) {
  return null !== e3.renderBaseName && t2.has(e3) && e3.isReassigned;
}
class Mr extends Zs {
  declareDeclarator(e3) {
    this.id.declare(e3, this.init || is);
  }
  deoptimizePath(e3) {
    this.id.deoptimizePath(e3);
  }
  hasEffects(e3) {
    var _a2;
    this.deoptimized || this.applyDeoptimizations();
    const t2 = (_a2 = this.init) == null ? void 0 : _a2.hasEffects(e3);
    return this.id.markDeclarationReached(), t2 || this.id.hasEffects(e3);
  }
  include(e3, t2) {
    const { deoptimized: s2, id: i2, init: n2 } = this;
    s2 || this.applyDeoptimizations(), this.included = true, n2 == null ? void 0 : n2.include(e3, t2), i2.markDeclarationReached(), (t2 || i2.shouldBeIncluded(e3)) && i2.include(e3, t2);
  }
  render(e3, t2) {
    const { exportNamesByVariable: s2, snippets: { _: i2, getPropertyAccess: n2 } } = t2, { end: r2, id: o2, init: a2, start: l2 } = this, h2 = o2.included;
    if (h2)
      o2.render(e3, t2);
    else {
      const t3 = un(e3.original, "=", o2.end);
      e3.remove(l2, pn(e3.original, t3 + 1));
    }
    if (a2) {
      if (o2 instanceof on && a2 instanceof Yn && !a2.id) {
        o2.variable.getName(n2) !== o2.name && e3.appendLeft(a2.start + 5, ` ${o2.name}`);
      }
      a2.render(e3, t2, h2 ? pe : { renderedSurroundingElement: Ns });
    } else
      o2 instanceof on && Tr(o2.variable, s2) && e3.appendLeft(r2, `${i2}=${i2}void 0`);
  }
  applyDeoptimizations() {
    this.deoptimized = true;
    const { id: e3, init: t2 } = this;
    if (t2 && e3 instanceof on && t2 instanceof Yn && !t2.id) {
      const { name: s2, variable: i2 } = e3;
      for (const e4 of t2.scope.accessedOutsideVariables.values())
        e4 !== i2 && e4.forbidName(s2);
    }
  }
}
function Vr(e3, t2, s2) {
  return "external" === t2 ? xr[s2(e3 instanceof Qt ? e3.id : null)] : "default" === t2 ? fr : null;
}
const Br = { amd: ["require"], cjs: ["require"], system: ["module"] };
function zr(e3) {
  const t2 = [];
  for (const s2 of e3.properties) {
    if ("RestElement" === s2.type || s2.computed || "Identifier" !== s2.key.type)
      return;
    t2.push(s2.key.name);
  }
  return t2;
}
class Fr extends Zs {
  applyDeoptimizations() {
  }
}
const jr = "ROLLUP_FILE_URL_", Ur = "import";
const Gr = { amd: ["document", "module", "URL"], cjs: ["document", "require", "URL"], es: [], iife: ["document", "URL"], system: ["module"], umd: ["document", "require", "URL"] }, Wr = { amd: ["document", "require", "URL"], cjs: ["document", "require", "URL"], es: [], iife: ["document", "URL"], system: ["module", "URL"], umd: ["document", "require", "URL"] }, qr = (e3, t2 = "URL") => `new ${t2}(${e3}).href`, Hr = (e3, t2 = false) => qr(`'${D(e3)}', ${t2 ? "typeof document === 'undefined' ? location.href : " : ""}document.currentScript && document.currentScript.src || document.baseURI`), Kr = (e3) => (t2, { chunkId: s2 }) => {
  const i2 = e3(s2);
  return null === t2 ? `({ url: ${i2} })` : "url" === t2 ? i2 : "undefined";
}, Yr = (e3) => `require('u' + 'rl').pathToFileURL(${e3}).href`, Xr = (e3) => Yr(`__dirname + '/${e3}'`), Qr = (e3, t2 = false) => `${t2 ? "typeof document === 'undefined' ? location.href : " : ""}(document.currentScript && document.currentScript.src || new URL('${D(e3)}', document.baseURI).href)`, Zr = { amd: (e3) => ("." !== e3[0] && (e3 = "./" + e3), qr(`require.toUrl('${e3}'), document.baseURI`)), cjs: (e3) => `(typeof document === 'undefined' ? ${Xr(e3)} : ${Hr(e3)})`, es: (e3) => qr(`'${e3}', import.meta.url`), iife: (e3) => Hr(e3), system: (e3) => qr(`'${e3}', module.meta.url`), umd: (e3) => `(typeof document === 'undefined' && typeof location === 'undefined' ? ${Xr(e3)} : ${Hr(e3, true)})` }, Jr = { amd: Kr(() => qr("module.uri, document.baseURI")), cjs: Kr((e3) => `(typeof document === 'undefined' ? ${Yr("__filename")} : ${Qr(e3)})`), iife: Kr((e3) => Qr(e3)), system: (e3, { snippets: { getPropertyAccess: t2 } }) => null === e3 ? "module.meta" : `module.meta${t2(e3)}`, umd: Kr((e3) => `(typeof document === 'undefined' && typeof location === 'undefined' ? ${Yr("__filename")} : ${Qr(e3, true)})`) };
class eo extends Zs {
  constructor() {
    super(...arguments), this.hasCachedEffect = null, this.hasLoggedEffect = false;
  }
  hasCachedEffects() {
    return !!this.included && (null === this.hasCachedEffect ? this.hasCachedEffect = this.hasEffects(ts()) : this.hasCachedEffect);
  }
  hasEffects(e3) {
    for (const t2 of this.body)
      if (t2.hasEffects(e3)) {
        if (this.context.options.experimentalLogSideEffects && !this.hasLoggedEffect) {
          this.hasLoggedEffect = true;
          const { code: e4, log: s2, module: i2 } = this.context;
          s2(Se, Lt(e4, i2.id, we(e4, t2.start, { offsetLine: 1 })), t2.start);
        }
        return this.hasCachedEffect = true;
      }
    return false;
  }
  include(e3, t2) {
    this.included = true;
    for (const s2 of this.body)
      (t2 || s2.shouldBeIncluded(e3)) && s2.include(e3, t2);
  }
  render(e3, t2) {
    let s2 = this.start;
    if (e3.original.startsWith("#!") && (s2 = Math.min(e3.original.indexOf("\n") + 1, this.end), e3.remove(0, s2)), this.body.length > 0) {
      for (; "/" === e3.original[s2] && /[*/]/.test(e3.original[s2 + 1]); ) {
        const t3 = fn(e3.original.slice(s2, this.body[0].start));
        if (-1 === t3[0])
          break;
        s2 += t3[1];
      }
      mn(this.body, e3, s2, this.end, t2);
    } else
      super.render(e3, t2);
  }
  applyDeoptimizations() {
  }
}
class to extends Zs {
  hasEffects(e3) {
    var _a2;
    if ((_a2 = this.test) == null ? void 0 : _a2.hasEffects(e3))
      return true;
    for (const t2 of this.consequent) {
      if (e3.brokenFlow)
        break;
      if (t2.hasEffects(e3))
        return true;
    }
    return false;
  }
  include(e3, t2) {
    var _a2;
    this.included = true, (_a2 = this.test) == null ? void 0 : _a2.include(e3, t2);
    for (const s2 of this.consequent)
      (t2 || s2.shouldBeIncluded(e3)) && s2.include(e3, t2);
  }
  render(e3, t2, s2) {
    if (this.consequent.length > 0) {
      this.test && this.test.render(e3, t2);
      const i2 = this.test ? this.test.end : un(e3.original, "default", this.start) + 7, n2 = un(e3.original, ":", i2) + 1;
      mn(this.consequent, e3, n2, s2.end, t2);
    } else
      super.render(e3, t2);
  }
}
to.prototype.needsBoundaries = true;
class so extends Zs {
  deoptimizeArgumentsOnInteractionAtPath() {
  }
  getLiteralValueAtPath(e3) {
    return e3.length > 0 || 1 !== this.quasis.length ? se : this.quasis[0].value.cooked;
  }
  getReturnExpressionWhenCalledAtPath(e3) {
    return 1 !== e3.length ? oe : xs(gs, e3[0]);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return 0 === t2.type ? e3.length > 1 : 2 !== t2.type || 1 !== e3.length || ys(gs, e3[0], t2, s2);
  }
  render(e3, t2) {
    e3.indentExclusionRanges.push([this.start, this.end]), super.render(e3, t2);
  }
}
class io extends ue {
  constructor() {
    super("undefined");
  }
  getLiteralValueAtPath() {
  }
}
class no extends Ii {
  constructor(e3, t2, s2) {
    super(e3, t2, t2.declaration, s2), this.hasId = false, this.originalId = null, this.originalVariable = null;
    const i2 = t2.declaration;
    (i2 instanceof er || i2 instanceof Kn) && i2.id ? (this.hasId = true, this.originalId = i2.id) : i2 instanceof on && (this.originalId = i2);
  }
  addReference(e3) {
    this.hasId || (this.name = e3.name);
  }
  forbidName(e3) {
    const t2 = this.getOriginalVariable();
    t2 === this ? super.forbidName(e3) : t2.forbidName(e3);
  }
  getAssignedVariableName() {
    return this.originalId && this.originalId.name || null;
  }
  getBaseVariableName() {
    const e3 = this.getOriginalVariable();
    return e3 === this ? super.getBaseVariableName() : e3.getBaseVariableName();
  }
  getDirectOriginalVariable() {
    return !this.originalId || !this.hasId && (this.originalId.isPossibleTDZ() || this.originalId.variable.isReassigned || this.originalId.variable instanceof io || "syntheticNamespace" in this.originalId.variable) ? null : this.originalId.variable;
  }
  getName(e3) {
    const t2 = this.getOriginalVariable();
    return t2 === this ? super.getName(e3) : t2.getName(e3);
  }
  getOriginalVariable() {
    if (this.originalVariable)
      return this.originalVariable;
    let e3, t2 = this;
    const s2 = /* @__PURE__ */ new Set();
    do {
      s2.add(t2), e3 = t2, t2 = e3.getDirectOriginalVariable();
    } while (t2 instanceof no && !s2.has(t2));
    return this.originalVariable = t2 || e3;
  }
}
class ro extends Ti {
  constructor(e3, t2) {
    super(e3), this.context = t2, this.variables.set("this", new Ii("this", null, is, t2));
  }
  addExportDefaultDeclaration(e3, t2, s2) {
    const i2 = new no(e3, t2, s2);
    return this.variables.set("default", i2), i2;
  }
  addNamespaceMemberAccess() {
  }
  deconflict(e3, t2, s2) {
    for (const i2 of this.children)
      i2.deconflict(e3, t2, s2);
  }
  findLexicalBoundary() {
    return this;
  }
  findVariable(e3) {
    const t2 = this.variables.get(e3) || this.accessedOutsideVariables.get(e3);
    if (t2)
      return t2;
    const s2 = this.context.traceVariable(e3) || this.parent.findVariable(e3);
    return s2 instanceof nn && this.accessedOutsideVariables.set(e3, s2), s2;
  }
}
const oo = { "!": (e3) => !e3, "+": (e3) => +e3, "-": (e3) => -e3, delete: () => se, typeof: (e3) => typeof e3, void: () => {
}, "~": (e3) => ~e3 };
class ao extends Zs {
  deoptimizePath() {
    for (const e3 of this.declarations)
      e3.deoptimizePath(K);
  }
  hasEffectsOnInteractionAtPath() {
    return false;
  }
  include(e3, t2, { asSingleStatement: s2 } = pe) {
    this.included = true;
    for (const i2 of this.declarations) {
      (t2 || i2.shouldBeIncluded(e3)) && i2.include(e3, t2);
      const { id: n2, init: r2 } = i2;
      s2 && n2.include(e3, t2), r2 && n2.included && !r2.included && (n2 instanceof Pn || n2 instanceof ki) && r2.include(e3, t2);
    }
  }
  initialise() {
    for (const e3 of this.declarations)
      e3.declareDeclarator(this.kind);
  }
  render(e3, t2, s2 = pe) {
    if (function(e4, t3) {
      for (const s3 of e4) {
        if (!s3.id.included)
          return false;
        if (s3.id.type === Rs) {
          if (t3.has(s3.id.variable))
            return false;
        } else {
          const e5 = [];
          if (s3.id.addExportedVariables(e5, t3), e5.length > 0)
            return false;
        }
      }
      return true;
    }(this.declarations, t2.exportNamesByVariable)) {
      for (const s3 of this.declarations)
        s3.render(e3, t2);
      s2.isNoStatement || 59 === e3.original.charCodeAt(this.end - 1) || e3.appendLeft(this.end, ";");
    } else
      this.renderReplacedDeclarations(e3, t2);
  }
  applyDeoptimizations() {
  }
  renderDeclarationEnd(e3, t2, s2, i2, n2, r2, o2) {
    59 === e3.original.charCodeAt(this.end - 1) && e3.remove(this.end - 1, this.end), t2 += ";", null === s2 ? e3.appendLeft(n2, t2) : (10 !== e3.original.charCodeAt(i2 - 1) || 10 !== e3.original.charCodeAt(this.end) && 13 !== e3.original.charCodeAt(this.end) || (i2--, 13 === e3.original.charCodeAt(i2) && i2--), i2 === s2 + 1 ? e3.overwrite(s2, n2, t2) : (e3.overwrite(s2, s2 + 1, t2), e3.remove(i2, n2))), r2.length > 0 && e3.appendLeft(n2, ` ${kn(r2, o2)};`);
  }
  renderReplacedDeclarations(e3, t2) {
    const s2 = gn(this.declarations, e3, this.start + this.kind.length, this.end - (59 === e3.original.charCodeAt(this.end - 1) ? 1 : 0));
    let i2, n2;
    n2 = pn(e3.original, this.start + this.kind.length);
    let r2 = n2 - 1;
    e3.remove(this.start, r2);
    let o2, l2 = false, h2 = false, c2 = "";
    const u2 = [], d2 = function(e4, t3, s3) {
      var _a2;
      let i3 = null;
      if ("system" === t3.format) {
        for (const { node: n3 } of e4)
          n3.id instanceof on && n3.init && 0 === s3.length && 1 === ((_a2 = t3.exportNamesByVariable.get(n3.id.variable)) == null ? void 0 : _a2.length) ? (i3 = n3.id.variable, s3.push(i3)) : n3.id.addExportedVariables(s3, t3.exportNamesByVariable);
        s3.length > 1 ? i3 = null : i3 && (s3.length = 0);
      }
      return i3;
    }(s2, t2, u2);
    for (const { node: u3, start: p2, separator: f2, contentEnd: m2, end: g2 } of s2)
      if (u3.included) {
        if (u3.render(e3, t2), o2 = "", !u3.id.included || u3.id instanceof on && Tr(u3.id.variable, t2.exportNamesByVariable))
          h2 && (c2 += ";"), l2 = false;
        else {
          if (d2 && d2 === u3.id.variable) {
            const s3 = un(e3.original, "=", u3.id.end);
            In(d2, pn(e3.original, s3 + 1), null === f2 ? m2 : f2, e3, t2);
          }
          l2 ? c2 += "," : (h2 && (c2 += ";"), o2 += `${this.kind} `, l2 = true);
        }
        n2 === r2 + 1 ? e3.overwrite(r2, n2, c2 + o2) : (e3.overwrite(r2, r2 + 1, c2), e3.appendLeft(n2, o2)), i2 = m2, n2 = g2, h2 = true, r2 = f2, c2 = "";
      } else
        e3.remove(p2, g2);
    this.renderDeclarationEnd(e3, c2, r2, i2, n2, u2, t2);
  }
}
const lo = { ArrayExpression: Ai, ArrayPattern: ki, ArrowFunctionExpression: An, AssignmentExpression: class extends Zs {
  hasEffects(e3) {
    const { deoptimized: t2, left: s2, operator: i2, right: n2 } = this;
    return t2 || this.applyDeoptimizations(), n2.hasEffects(e3) || s2.hasEffectsAsAssignmentTarget(e3, "=" !== i2);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return this.right.hasEffectsOnInteractionAtPath(e3, t2, s2);
  }
  include(e3, t2) {
    const { deoptimized: s2, left: i2, right: n2, operator: r2 } = this;
    s2 || this.applyDeoptimizations(), this.included = true, (t2 || "=" !== r2 || i2.included || i2.hasEffectsAsAssignmentTarget(ts(), false)) && i2.includeAsAssignmentTarget(e3, t2, "=" !== r2), n2.include(e3, t2);
  }
  initialise() {
    this.left.setAssignedValue(this.right);
  }
  render(e3, t2, { preventASI: s2, renderedParentType: i2, renderedSurroundingElement: n2 } = pe) {
    const { left: r2, right: o2, start: a2, end: l2, parent: h2 } = this;
    if (r2.included)
      r2.render(e3, t2), o2.render(e3, t2);
    else {
      const l3 = pn(e3.original, un(e3.original, "=", r2.end) + 1);
      e3.remove(a2, l3), s2 && yn(e3, l3, o2.start), o2.render(e3, t2, { renderedParentType: i2 || h2.type, renderedSurroundingElement: n2 || h2.type });
    }
    if ("system" === t2.format)
      if (r2 instanceof on) {
        const s3 = r2.variable, i3 = t2.exportNamesByVariable.get(s3);
        if (i3)
          return void (1 === i3.length ? In(s3, a2, l2, e3, t2) : wn(s3, a2, l2, h2.type !== Ns, e3, t2));
      } else {
        const s3 = [];
        if (r2.addExportedVariables(s3, t2.exportNamesByVariable), s3.length > 0)
          return void function(e4, t3, s4, i3, n3, r3) {
            const { _: o3, getDirectReturnIifeLeft: a3 } = r3.snippets;
            n3.prependRight(t3, a3(["v"], `${kn(e4, r3)},${o3}v`, { needsArrowReturnParens: true, needsWrappedFunction: i3 })), n3.appendLeft(s4, ")");
          }(s3, a2, l2, n2 === Ns, e3, t2);
      }
    r2.included && r2 instanceof Pn && (n2 === Ns || n2 === Ss) && (e3.appendRight(a2, "("), e3.prependLeft(l2, ")"));
  }
  applyDeoptimizations() {
    this.deoptimized = true, this.left.deoptimizePath(K), this.right.deoptimizePath(Y), this.context.requestTreeshakingPass();
  }
}, AssignmentPattern: class extends Zs {
  addExportedVariables(e3, t2) {
    this.left.addExportedVariables(e3, t2);
  }
  declare(e3, t2) {
    return this.left.declare(e3, t2);
  }
  deoptimizePath(e3) {
    0 === e3.length && this.left.deoptimizePath(e3);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return e3.length > 0 || this.left.hasEffectsOnInteractionAtPath(K, t2, s2);
  }
  markDeclarationReached() {
    this.left.markDeclarationReached();
  }
  render(e3, t2, { isShorthandProperty: s2 } = pe) {
    this.left.render(e3, t2, { isShorthandProperty: s2 }), this.right.render(e3, t2);
  }
  applyDeoptimizations() {
    this.deoptimized = true, this.left.deoptimizePath(K), this.right.deoptimizePath(Y), this.context.requestTreeshakingPass();
  }
}, AwaitExpression: Rn, BinaryExpression: class extends Zs {
  deoptimizeCache() {
  }
  getLiteralValueAtPath(e3, t2, s2) {
    if (e3.length > 0)
      return se;
    const i2 = this.left.getLiteralValueAtPath(K, t2, s2);
    if ("symbol" == typeof i2)
      return se;
    const n2 = this.right.getLiteralValueAtPath(K, t2, s2);
    if ("symbol" == typeof n2)
      return se;
    const r2 = On[this.operator];
    return r2 ? r2(i2, n2) : se;
  }
  hasEffects(e3) {
    return "+" === this.operator && this.parent instanceof En && "" === this.left.getLiteralValueAtPath(K, ee, this) || super.hasEffects(e3);
  }
  hasEffectsOnInteractionAtPath(e3, { type: t2 }) {
    return 0 !== t2 || e3.length > 1;
  }
  render(e3, t2, { renderedSurroundingElement: s2 } = pe) {
    this.left.render(e3, t2, { renderedSurroundingElement: s2 }), this.right.render(e3, t2);
  }
}, BlockStatement: bn, BreakStatement: class extends Zs {
  hasEffects(e3) {
    if (this.label) {
      if (!e3.ignore.labels.has(this.label.name))
        return true;
      e3.includedLabels.add(this.label.name);
    } else {
      if (!e3.ignore.breaks)
        return true;
      e3.hasBreak = true;
    }
    return e3.brokenFlow = true, false;
  }
  include(e3) {
    this.included = true, this.label ? (this.label.include(), e3.includedLabels.add(this.label.name)) : e3.hasBreak = true, e3.brokenFlow = true;
  }
}, CallExpression: Fn, CatchClause: class extends Zs {
  createScope(e3) {
    this.scope = new jn(e3, this.context);
  }
  parseNode(e3) {
    const { param: t2 } = e3;
    t2 && (this.param = new (this.context.getNodeConstructor(t2.type))(t2, this, this.scope), this.param.declare("parameter", re)), super.parseNode(e3);
  }
}, ChainExpression: class extends Zs {
  deoptimizeCache() {
  }
  getLiteralValueAtPath(e3, t2, s2) {
    if (!this.expression.isSkippedAsOptional(s2))
      return this.expression.getLiteralValueAtPath(e3, t2, s2);
  }
  hasEffects(e3) {
    return !this.expression.isSkippedAsOptional(this) && this.expression.hasEffects(e3);
  }
}, ClassBody: class extends Zs {
  createScope(e3) {
    this.scope = new Un(e3, this.parent, this.context);
  }
  include(e3, t2) {
    this.included = true, this.context.includeVariableInModule(this.scope.thisVariable);
    for (const s2 of this.body)
      s2.include(e3, t2);
  }
  parseNode(e3) {
    const t2 = this.body = [];
    for (const s2 of e3.body)
      t2.push(new (this.context.getNodeConstructor(s2.type))(s2, this, s2.static ? this.scope : this.scope.instanceScope));
    super.parseNode(e3);
  }
  applyDeoptimizations() {
  }
}, ClassDeclaration: Kn, ClassExpression: Yn, ConditionalExpression: class extends Zs {
  constructor() {
    super(...arguments), this.expressionsToBeDeoptimized = [], this.isBranchResolutionAnalysed = false, this.usedBranch = null;
  }
  deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2) {
    this.consequent.deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2), this.alternate.deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2);
  }
  deoptimizeCache() {
    if (null !== this.usedBranch) {
      const e3 = this.usedBranch === this.consequent ? this.alternate : this.consequent;
      this.usedBranch = null, e3.deoptimizePath(Y);
      const { expressionsToBeDeoptimized: t2 } = this;
      this.expressionsToBeDeoptimized = me;
      for (const e4 of t2)
        e4.deoptimizeCache();
    }
  }
  deoptimizePath(e3) {
    const t2 = this.getUsedBranch();
    t2 ? t2.deoptimizePath(e3) : (this.consequent.deoptimizePath(e3), this.alternate.deoptimizePath(e3));
  }
  getLiteralValueAtPath(e3, t2, s2) {
    const i2 = this.getUsedBranch();
    return i2 ? (this.expressionsToBeDeoptimized.push(s2), i2.getLiteralValueAtPath(e3, t2, s2)) : se;
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    const n2 = this.getUsedBranch();
    return n2 ? (this.expressionsToBeDeoptimized.push(i2), n2.getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2)) : [new Xn([this.consequent.getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2)[0], this.alternate.getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2)[0]]), false];
  }
  hasEffects(e3) {
    if (this.test.hasEffects(e3))
      return true;
    const t2 = this.getUsedBranch();
    return t2 ? t2.hasEffects(e3) : this.consequent.hasEffects(e3) || this.alternate.hasEffects(e3);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    const i2 = this.getUsedBranch();
    return i2 ? i2.hasEffectsOnInteractionAtPath(e3, t2, s2) : this.consequent.hasEffectsOnInteractionAtPath(e3, t2, s2) || this.alternate.hasEffectsOnInteractionAtPath(e3, t2, s2);
  }
  include(e3, t2) {
    this.included = true;
    const s2 = this.getUsedBranch();
    t2 || this.test.shouldBeIncluded(e3) || null === s2 ? (this.test.include(e3, t2), this.consequent.include(e3, t2), this.alternate.include(e3, t2)) : s2.include(e3, t2);
  }
  includeCallArguments(e3, t2) {
    const s2 = this.getUsedBranch();
    s2 ? s2.includeCallArguments(e3, t2) : (this.consequent.includeCallArguments(e3, t2), this.alternate.includeCallArguments(e3, t2));
  }
  render(e3, t2, { isCalleeOfRenderedParent: s2, preventASI: i2, renderedParentType: n2, renderedSurroundingElement: r2 } = pe) {
    const o2 = this.getUsedBranch();
    if (this.test.included)
      this.test.render(e3, t2, { renderedSurroundingElement: r2 }), this.consequent.render(e3, t2), this.alternate.render(e3, t2);
    else {
      const a2 = un(e3.original, ":", this.consequent.end), l2 = pn(e3.original, (this.consequent.included ? un(e3.original, "?", this.test.end) : a2) + 1);
      i2 && yn(e3, l2, o2.start), e3.remove(this.start, l2), this.consequent.included && e3.remove(a2, this.end), hn(this, e3), o2.render(e3, t2, { isCalleeOfRenderedParent: s2, preventASI: true, renderedParentType: n2 || this.parent.type, renderedSurroundingElement: r2 || this.parent.type });
    }
  }
  getUsedBranch() {
    if (this.isBranchResolutionAnalysed)
      return this.usedBranch;
    this.isBranchResolutionAnalysed = true;
    const e3 = this.test.getLiteralValueAtPath(K, ee, this);
    return "symbol" == typeof e3 ? null : this.usedBranch = e3 ? this.consequent : this.alternate;
  }
}, ContinueStatement: class extends Zs {
  hasEffects(e3) {
    if (this.label) {
      if (!e3.ignore.labels.has(this.label.name))
        return true;
      e3.includedLabels.add(this.label.name);
    } else {
      if (!e3.ignore.continues)
        return true;
      e3.hasContinue = true;
    }
    return e3.brokenFlow = true, false;
  }
  include(e3) {
    this.included = true, this.label ? (this.label.include(), e3.includedLabels.add(this.label.name)) : e3.hasContinue = true, e3.brokenFlow = true;
  }
}, DoWhileStatement: class extends Zs {
  hasEffects(e3) {
    return !!this.test.hasEffects(e3) || Qn(e3, this.body);
  }
  include(e3, t2) {
    this.included = true, this.test.include(e3, t2), Zn(e3, this.body, t2);
  }
}, EmptyStatement: class extends Zs {
  hasEffects() {
    return false;
  }
}, ExportAllDeclaration: Jn, ExportDefaultDeclaration: tr, ExportNamedDeclaration: sr, ExportSpecifier: class extends Zs {
  applyDeoptimizations() {
  }
}, ExpressionStatement: En, ForInStatement: class extends Zs {
  createScope(e3) {
    this.scope = new xn(e3);
  }
  hasEffects(e3) {
    const { body: t2, deoptimized: s2, left: i2, right: n2 } = this;
    return s2 || this.applyDeoptimizations(), !(!i2.hasEffectsAsAssignmentTarget(e3, false) && !n2.hasEffects(e3)) || Qn(e3, t2);
  }
  include(e3, t2) {
    const { body: s2, deoptimized: i2, left: n2, right: r2 } = this;
    i2 || this.applyDeoptimizations(), this.included = true, n2.includeAsAssignmentTarget(e3, t2 || true, false), r2.include(e3, t2), Zn(e3, s2, t2);
  }
  initialise() {
    this.left.setAssignedValue(re);
  }
  render(e3, t2) {
    this.left.render(e3, t2, cn), this.right.render(e3, t2, cn), 110 === e3.original.charCodeAt(this.right.start - 1) && e3.prependLeft(this.right.start, " "), this.body.render(e3, t2);
  }
  applyDeoptimizations() {
    this.deoptimized = true, this.left.deoptimizePath(K), this.context.requestTreeshakingPass();
  }
}, ForOfStatement: class extends Zs {
  createScope(e3) {
    this.scope = new xn(e3);
  }
  hasEffects() {
    return this.deoptimized || this.applyDeoptimizations(), true;
  }
  include(e3, t2) {
    const { body: s2, deoptimized: i2, left: n2, right: r2 } = this;
    i2 || this.applyDeoptimizations(), this.included = true, n2.includeAsAssignmentTarget(e3, t2 || true, false), r2.include(e3, t2), Zn(e3, s2, t2);
  }
  initialise() {
    this.left.setAssignedValue(re);
  }
  render(e3, t2) {
    this.left.render(e3, t2, cn), this.right.render(e3, t2, cn), 102 === e3.original.charCodeAt(this.right.start - 1) && e3.prependLeft(this.right.start, " "), this.body.render(e3, t2);
  }
  applyDeoptimizations() {
    this.deoptimized = true, this.left.deoptimizePath(K), this.right.deoptimizePath(Y), this.context.requestTreeshakingPass();
  }
}, ForStatement: class extends Zs {
  createScope(e3) {
    this.scope = new xn(e3);
  }
  hasEffects(e3) {
    var _a2, _b, _c2;
    return !!(((_a2 = this.init) == null ? void 0 : _a2.hasEffects(e3)) || ((_b = this.test) == null ? void 0 : _b.hasEffects(e3)) || ((_c2 = this.update) == null ? void 0 : _c2.hasEffects(e3))) || Qn(e3, this.body);
  }
  include(e3, t2) {
    var _a2, _b, _c2;
    this.included = true, (_a2 = this.init) == null ? void 0 : _a2.include(e3, t2, { asSingleStatement: true }), (_b = this.test) == null ? void 0 : _b.include(e3, t2), (_c2 = this.update) == null ? void 0 : _c2.include(e3, t2), Zn(e3, this.body, t2);
  }
  render(e3, t2) {
    var _a2, _b, _c2;
    (_a2 = this.init) == null ? void 0 : _a2.render(e3, t2, cn), (_b = this.test) == null ? void 0 : _b.render(e3, t2, cn), (_c2 = this.update) == null ? void 0 : _c2.render(e3, t2, cn), this.body.render(e3, t2);
  }
}, FunctionDeclaration: er, FunctionExpression: ir, Identifier: on, IfStatement: or, ImportAttribute: class extends Zs {
}, ImportDeclaration: ar, ImportDefaultSpecifier: lr, ImportExpression: class extends Zs {
  constructor() {
    super(...arguments), this.inlineNamespace = null, this.assertions = null, this.mechanism = null, this.namespaceExportName = void 0, this.resolution = null, this.resolutionString = null;
  }
  bind() {
    this.source.bind();
  }
  getDeterministicImportedNames() {
    const e3 = this.parent;
    if (e3 instanceof En)
      return me;
    if (e3 instanceof Rn) {
      const t2 = e3.parent;
      if (t2 instanceof En)
        return me;
      if (t2 instanceof Mr) {
        const e4 = t2.id;
        return e4 instanceof Pn ? zr(e4) : void 0;
      }
      if (t2 instanceof Vn) {
        const e4 = t2.property;
        if (!t2.computed && e4 instanceof on)
          return [e4.name];
      }
    } else if (e3 instanceof Vn) {
      const t2 = e3.parent, s2 = e3.property;
      if (!(t2 instanceof Fn && s2 instanceof on))
        return;
      const i2 = s2.name;
      if (t2.parent instanceof En && ["catch", "finally"].includes(i2))
        return me;
      if ("then" !== i2)
        return;
      if (0 === t2.arguments.length)
        return me;
      const n2 = t2.arguments[0];
      if (1 !== t2.arguments.length || !(n2 instanceof An || n2 instanceof ir))
        return;
      if (0 === n2.params.length)
        return me;
      const r2 = n2.params[0];
      return 1 === n2.params.length && r2 instanceof Pn ? zr(r2) : void 0;
    }
  }
  hasEffects() {
    return true;
  }
  include(e3, t2) {
    this.included || (this.included = true, this.context.includeDynamicImport(this), this.scope.addAccessedDynamicImport(this)), this.source.include(e3, t2);
  }
  initialise() {
    this.context.addDynamicImport(this);
  }
  parseNode(e3) {
    super.parseNode(e3, ["source"]);
  }
  render(e3, t2) {
    const { snippets: { _: s2, getDirectReturnFunction: i2, getObject: n2, getPropertyAccess: r2 } } = t2;
    if (this.inlineNamespace) {
      const [t3, s3] = i2([], { functionReturn: true, lineBreakIndent: null, name: null });
      e3.overwrite(this.start, this.end, `Promise.resolve().then(${t3}${this.inlineNamespace.getName(r2)}${s3})`);
    } else {
      if (this.mechanism && (e3.overwrite(this.start, un(e3.original, "(", this.start + 6) + 1, this.mechanism.left), e3.overwrite(this.end - 1, this.end, this.mechanism.right)), this.resolutionString) {
        if (e3.overwrite(this.source.start, this.source.end, this.resolutionString), this.namespaceExportName) {
          const [t3, s3] = i2(["n"], { functionReturn: true, lineBreakIndent: null, name: null });
          e3.prependLeft(this.end, `.then(${t3}n.${this.namespaceExportName}${s3})`);
        }
      } else
        this.source.render(e3, t2);
      true !== this.assertions && (this.arguments && e3.overwrite(this.source.end, this.end - 1, "", { contentOnly: true }), this.assertions && e3.appendLeft(this.end - 1, `,${s2}${n2([["assert", this.assertions]], { lineBreakIndent: null })}`));
    }
  }
  setExternalResolution(e3, t2, s2, i2, n2, r2, o2, a2, l2) {
    const { format: h2 } = s2;
    this.inlineNamespace = null, this.resolution = t2, this.resolutionString = o2, this.namespaceExportName = a2, this.assertions = l2;
    const c2 = [...Br[h2] || []];
    let u2;
    ({ helper: u2, mechanism: this.mechanism } = this.getDynamicImportMechanismAndHelper(t2, e3, s2, i2, n2)), u2 && c2.push(u2), c2.length > 0 && this.scope.addAccessedGlobals(c2, r2);
  }
  setInternalResolution(e3) {
    this.inlineNamespace = e3;
  }
  applyDeoptimizations() {
  }
  getDynamicImportMechanismAndHelper(e3, t2, { compact: s2, dynamicImportFunction: i2, dynamicImportInCjs: n2, format: r2, generatedCode: { arrowFunctions: o2 }, interop: a2 }, { _: l2, getDirectReturnFunction: h2, getDirectReturnIifeLeft: c2 }, u2) {
    const d2 = u2.hookFirstSync("renderDynamicImport", [{ customResolution: "string" == typeof this.resolution ? this.resolution : null, format: r2, moduleId: this.context.module.id, targetModuleId: this.resolution && "string" != typeof this.resolution ? this.resolution.id : null }]);
    if (d2)
      return { helper: null, mechanism: d2 };
    const p2 = !this.resolution || "string" == typeof this.resolution;
    switch (r2) {
      case "cjs": {
        if (n2 && (!e3 || "string" == typeof e3 || e3 instanceof Qt))
          return { helper: null, mechanism: null };
        const s3 = Vr(e3, t2, a2);
        let i3 = "require(", r3 = ")";
        s3 && (i3 = `/*#__PURE__*/${s3}(${i3}`, r3 += ")");
        const [l3, u3] = h2([], { functionReturn: true, lineBreakIndent: null, name: null });
        return i3 = `Promise.resolve().then(${l3}${i3}`, r3 += `${u3})`, !o2 && p2 && (i3 = c2(["t"], `${i3}t${r3}`, { needsArrowReturnParens: false, needsWrappedFunction: true }), r3 = ")"), { helper: s3, mechanism: { left: i3, right: r3 } };
      }
      case "amd": {
        const i3 = s2 ? "c" : "resolve", n3 = s2 ? "e" : "reject", r3 = Vr(e3, t2, a2), [u3, d3] = h2(["m"], { functionReturn: false, lineBreakIndent: null, name: null }), f2 = r3 ? `${u3}${i3}(/*#__PURE__*/${r3}(m))${d3}` : i3, [m2, g2] = h2([i3, n3], { functionReturn: false, lineBreakIndent: null, name: null });
        let y2 = `new Promise(${m2}require([`, x2 = `],${l2}${f2},${l2}${n3})${g2})`;
        return !o2 && p2 && (y2 = c2(["t"], `${y2}t${x2}`, { needsArrowReturnParens: false, needsWrappedFunction: true }), x2 = ")"), { helper: r3, mechanism: { left: y2, right: x2 } };
      }
      case "system":
        return { helper: null, mechanism: { left: "module.import(", right: ")" } };
      case "es":
        if (i2)
          return { helper: null, mechanism: { left: `${i2}(`, right: ")" } };
    }
    return { helper: null, mechanism: null };
  }
}, ImportNamespaceSpecifier: Fr, ImportSpecifier: class extends Zs {
  applyDeoptimizations() {
  }
}, LabeledStatement: class extends Zs {
  hasEffects(e3) {
    const t2 = e3.brokenFlow;
    return e3.ignore.labels.add(this.label.name), !!this.body.hasEffects(e3) || (e3.ignore.labels.delete(this.label.name), e3.includedLabels.has(this.label.name) && (e3.includedLabels.delete(this.label.name), e3.brokenFlow = t2), false);
  }
  include(e3, t2) {
    this.included = true;
    const s2 = e3.brokenFlow;
    this.body.include(e3, t2), (t2 || e3.includedLabels.has(this.label.name)) && (this.label.include(), e3.includedLabels.delete(this.label.name), e3.brokenFlow = s2);
  }
  render(e3, t2) {
    this.label.included ? this.label.render(e3, t2) : e3.remove(this.start, pn(e3.original, un(e3.original, ":", this.label.end) + 1)), this.body.render(e3, t2);
  }
}, Literal: Ln, LogicalExpression: class extends Zs {
  constructor() {
    super(...arguments), this.expressionsToBeDeoptimized = [], this.isBranchResolutionAnalysed = false, this.usedBranch = null;
  }
  deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2) {
    this.left.deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2), this.right.deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2);
  }
  deoptimizeCache() {
    if (this.usedBranch) {
      const e3 = this.usedBranch === this.left ? this.right : this.left;
      this.usedBranch = null, e3.deoptimizePath(Y);
      const { context: t2, expressionsToBeDeoptimized: s2 } = this;
      this.expressionsToBeDeoptimized = me;
      for (const e4 of s2)
        e4.deoptimizeCache();
      t2.requestTreeshakingPass();
    }
  }
  deoptimizePath(e3) {
    const t2 = this.getUsedBranch();
    t2 ? t2.deoptimizePath(e3) : (this.left.deoptimizePath(e3), this.right.deoptimizePath(e3));
  }
  getLiteralValueAtPath(e3, t2, s2) {
    const i2 = this.getUsedBranch();
    return i2 ? (this.expressionsToBeDeoptimized.push(s2), i2.getLiteralValueAtPath(e3, t2, s2)) : se;
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    const n2 = this.getUsedBranch();
    return n2 ? (this.expressionsToBeDeoptimized.push(i2), n2.getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2)) : [new Xn([this.left.getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2)[0], this.right.getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2)[0]]), false];
  }
  hasEffects(e3) {
    return !!this.left.hasEffects(e3) || this.getUsedBranch() !== this.left && this.right.hasEffects(e3);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    const i2 = this.getUsedBranch();
    return i2 ? i2.hasEffectsOnInteractionAtPath(e3, t2, s2) : this.left.hasEffectsOnInteractionAtPath(e3, t2, s2) || this.right.hasEffectsOnInteractionAtPath(e3, t2, s2);
  }
  include(e3, t2) {
    this.included = true;
    const s2 = this.getUsedBranch();
    t2 || s2 === this.right && this.left.shouldBeIncluded(e3) || !s2 ? (this.left.include(e3, t2), this.right.include(e3, t2)) : s2.include(e3, t2);
  }
  render(e3, t2, { isCalleeOfRenderedParent: s2, preventASI: i2, renderedParentType: n2, renderedSurroundingElement: r2 } = pe) {
    if (this.left.included && this.right.included)
      this.left.render(e3, t2, { preventASI: i2, renderedSurroundingElement: r2 }), this.right.render(e3, t2);
    else {
      const o2 = un(e3.original, this.operator, this.left.end);
      if (this.right.included) {
        const t3 = pn(e3.original, o2 + 2);
        e3.remove(this.start, t3), i2 && yn(e3, t3, this.right.start);
      } else
        e3.remove(o2, this.end);
      hn(this, e3), this.getUsedBranch().render(e3, t2, { isCalleeOfRenderedParent: s2, preventASI: i2, renderedParentType: n2 || this.parent.type, renderedSurroundingElement: r2 || this.parent.type });
    }
  }
  getUsedBranch() {
    if (!this.isBranchResolutionAnalysed) {
      this.isBranchResolutionAnalysed = true;
      const e3 = this.left.getLiteralValueAtPath(K, ee, this);
      if ("symbol" == typeof e3)
        return null;
      this.usedBranch = "||" === this.operator && e3 || "&&" === this.operator && !e3 || "??" === this.operator && null != e3 ? this.left : this.right;
    }
    return this.usedBranch;
  }
}, MemberExpression: Vn, MetaProperty: class extends Zs {
  constructor() {
    super(...arguments), this.metaProperty = null, this.preliminaryChunkId = null, this.referenceId = null;
  }
  getReferencedFileName(e3) {
    const { meta: { name: t2 }, metaProperty: s2 } = this;
    return t2 === Ur && (s2 == null ? void 0 : s2.startsWith(jr)) ? e3.getFileName(s2.slice(16)) : null;
  }
  hasEffects() {
    return false;
  }
  hasEffectsOnInteractionAtPath(e3, { type: t2 }) {
    return e3.length > 1 || 0 !== t2;
  }
  include() {
    if (!this.included && (this.included = true, this.meta.name === Ur)) {
      this.context.addImportMeta(this);
      const e3 = this.parent, t2 = this.metaProperty = e3 instanceof Vn && "string" == typeof e3.propertyKey ? e3.propertyKey : null;
      (t2 == null ? void 0 : t2.startsWith(jr)) && (this.referenceId = t2.slice(16));
    }
  }
  render(e3, { format: t2, pluginDriver: s2, snippets: i2 }) {
    var _a2;
    const { context: { module: { id: n2 } }, meta: { name: r2 }, metaProperty: o2, parent: a2, preliminaryChunkId: l2, referenceId: h2, start: c2, end: u2 } = this;
    if (r2 !== Ur)
      return;
    const d2 = l2;
    if (h2) {
      const i3 = s2.getFileName(h2), r3 = I($(P(d2), i3)), o3 = s2.hookFirstSync("resolveFileUrl", [{ chunkId: d2, fileName: i3, format: t2, moduleId: n2, referenceId: h2, relativePath: r3 }]) || Zr[t2](r3);
      return void e3.overwrite(a2.start, a2.end, o3, { contentOnly: true });
    }
    const p2 = s2.hookFirstSync("resolveImportMeta", [o2, { chunkId: d2, format: t2, moduleId: n2 }]) || ((_a2 = Jr[t2]) == null ? void 0 : _a2.call(Jr, o2, { chunkId: d2, snippets: i2 }));
    "string" == typeof p2 && (a2 instanceof Vn ? e3.overwrite(a2.start, a2.end, p2, { contentOnly: true }) : e3.overwrite(c2, u2, p2, { contentOnly: true }));
  }
  setResolution(e3, t2, s2) {
    var _a2;
    this.preliminaryChunkId = s2;
    const i2 = (((_a2 = this.metaProperty) == null ? void 0 : _a2.startsWith(jr)) ? Wr : Gr)[e3];
    i2.length > 0 && this.scope.addAccessedGlobals(i2, t2);
  }
}, MethodDefinition: Wn, NewExpression: class extends Zs {
  hasEffects(e3) {
    try {
      for (const t2 of this.arguments)
        if (t2.hasEffects(e3))
          return true;
      return !this.annotationPure && (this.callee.hasEffects(e3) || this.callee.hasEffectsOnInteractionAtPath(K, this.interaction, e3));
    } finally {
      this.deoptimized || this.applyDeoptimizations();
    }
  }
  hasEffectsOnInteractionAtPath(e3, { type: t2 }) {
    return e3.length > 0 || 0 !== t2;
  }
  include(e3, t2) {
    this.deoptimized || this.applyDeoptimizations(), t2 ? super.include(e3, t2) : (this.included = true, this.callee.include(e3, false)), this.callee.includeCallArguments(e3, this.arguments);
  }
  initialise() {
    this.interaction = { args: [null, ...this.arguments], type: 2, withNew: true };
  }
  render(e3, t2) {
    this.callee.render(e3, t2), Dn(e3, t2, this);
  }
  applyDeoptimizations() {
    this.deoptimized = true, this.callee.deoptimizeArgumentsOnInteractionAtPath(this.interaction, K, ee), this.context.requestTreeshakingPass();
  }
}, ObjectExpression: class extends Zs {
  constructor() {
    super(...arguments), this.objectEntity = null;
  }
  deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2) {
    this.getObjectEntity().deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2);
  }
  deoptimizeCache() {
    this.getObjectEntity().deoptimizeAllProperties();
  }
  deoptimizePath(e3) {
    this.getObjectEntity().deoptimizePath(e3);
  }
  getLiteralValueAtPath(e3, t2, s2) {
    return this.getObjectEntity().getLiteralValueAtPath(e3, t2, s2);
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    return this.getObjectEntity().getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return this.getObjectEntity().hasEffectsOnInteractionAtPath(e3, t2, s2);
  }
  render(e3, t2, { renderedSurroundingElement: s2 } = pe) {
    super.render(e3, t2), s2 !== Ns && s2 !== Ss || (e3.appendRight(this.start, "("), e3.prependLeft(this.end, ")"));
  }
  applyDeoptimizations() {
  }
  getObjectEntity() {
    if (null !== this.objectEntity)
      return this.objectEntity;
    let e3 = hi;
    const t2 = [];
    for (const s2 of this.properties) {
      if (s2 instanceof Js) {
        t2.push({ key: G, kind: "init", property: s2 });
        continue;
      }
      let i2;
      if (s2.computed) {
        const e4 = s2.key.getLiteralValueAtPath(K, ee, this);
        if ("symbol" == typeof e4) {
          t2.push({ key: G, kind: s2.kind, property: s2 });
          continue;
        }
        i2 = String(e4);
      } else if (i2 = s2.key instanceof on ? s2.key.name : String(s2.key.value), "__proto__" === i2 && "init" === s2.kind) {
        e3 = s2.value instanceof Ln && null === s2.value.value ? null : s2.value;
        continue;
      }
      t2.push({ key: i2, kind: s2.kind, property: s2 });
    }
    return this.objectEntity = new oi(t2, e3);
  }
}, ObjectPattern: Pn, PrivateIdentifier: class extends Zs {
}, Program: eo, Property: class extends Gn {
  constructor() {
    super(...arguments), this.declarationInit = null;
  }
  declare(e3, t2) {
    return this.declarationInit = t2, this.value.declare(e3, re);
  }
  hasEffects(e3) {
    this.deoptimized || this.applyDeoptimizations();
    const t2 = this.context.options.treeshake.propertyReadSideEffects;
    return "ObjectPattern" === this.parent.type && "always" === t2 || this.key.hasEffects(e3) || this.value.hasEffects(e3);
  }
  markDeclarationReached() {
    this.value.markDeclarationReached();
  }
  render(e3, t2) {
    this.shorthand || this.key.render(e3, t2), this.value.render(e3, t2, { isShorthandProperty: this.shorthand });
  }
  applyDeoptimizations() {
    this.deoptimized = true, null !== this.declarationInit && (this.declarationInit.deoptimizePath([G, G]), this.context.requestTreeshakingPass());
  }
}, PropertyDefinition: class extends Zs {
  deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2) {
    var _a2;
    (_a2 = this.value) == null ? void 0 : _a2.deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2);
  }
  deoptimizePath(e3) {
    var _a2;
    (_a2 = this.value) == null ? void 0 : _a2.deoptimizePath(e3);
  }
  getLiteralValueAtPath(e3, t2, s2) {
    return this.value ? this.value.getLiteralValueAtPath(e3, t2, s2) : se;
  }
  getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) {
    return this.value ? this.value.getReturnExpressionWhenCalledAtPath(e3, t2, s2, i2) : oe;
  }
  hasEffects(e3) {
    var _a2;
    return this.key.hasEffects(e3) || this.static && !!((_a2 = this.value) == null ? void 0 : _a2.hasEffects(e3));
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return !this.value || this.value.hasEffectsOnInteractionAtPath(e3, t2, s2);
  }
  applyDeoptimizations() {
  }
}, RestElement: vn, ReturnStatement: class extends Zs {
  hasEffects(e3) {
    var _a2;
    return !(e3.ignore.returnYield && !((_a2 = this.argument) == null ? void 0 : _a2.hasEffects(e3))) || (e3.brokenFlow = true, false);
  }
  include(e3, t2) {
    var _a2;
    this.included = true, (_a2 = this.argument) == null ? void 0 : _a2.include(e3, t2), e3.brokenFlow = true;
  }
  initialise() {
    this.scope.addReturnExpression(this.argument || re);
  }
  render(e3, t2) {
    this.argument && (this.argument.render(e3, t2, { preventASI: true }), this.argument.start === this.start + 6 && e3.prependLeft(this.start + 6, " "));
  }
}, SequenceExpression: class extends Zs {
  deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2) {
    this.expressions[this.expressions.length - 1].deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2);
  }
  deoptimizePath(e3) {
    this.expressions[this.expressions.length - 1].deoptimizePath(e3);
  }
  getLiteralValueAtPath(e3, t2, s2) {
    return this.expressions[this.expressions.length - 1].getLiteralValueAtPath(e3, t2, s2);
  }
  hasEffects(e3) {
    for (const t2 of this.expressions)
      if (t2.hasEffects(e3))
        return true;
    return false;
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return this.expressions[this.expressions.length - 1].hasEffectsOnInteractionAtPath(e3, t2, s2);
  }
  include(e3, t2) {
    this.included = true;
    const s2 = this.expressions[this.expressions.length - 1];
    for (const i2 of this.expressions)
      (t2 || i2 === s2 && !(this.parent instanceof En) || i2.shouldBeIncluded(e3)) && i2.include(e3, t2);
  }
  render(e3, t2, { renderedParentType: s2, isCalleeOfRenderedParent: i2, preventASI: n2 } = pe) {
    let r2 = 0, o2 = null;
    const a2 = this.expressions[this.expressions.length - 1];
    for (const { node: l2, separator: h2, start: c2, end: u2 } of gn(this.expressions, e3, this.start, this.end))
      if (l2.included)
        if (r2++, o2 = h2, 1 === r2 && n2 && yn(e3, c2, l2.start), 1 === r2) {
          const n3 = s2 || this.parent.type;
          l2.render(e3, t2, { isCalleeOfRenderedParent: i2 && l2 === a2, renderedParentType: n3, renderedSurroundingElement: n3 });
        } else
          l2.render(e3, t2);
      else
        ln(l2, e3, c2, u2);
    o2 && e3.remove(o2, this.end);
  }
}, SpreadElement: Js, StaticBlock: class extends Zs {
  createScope(e3) {
    this.scope = new xn(e3);
  }
  hasEffects(e3) {
    for (const t2 of this.body)
      if (t2.hasEffects(e3))
        return true;
    return false;
  }
  include(e3, t2) {
    this.included = true;
    for (const s2 of this.body)
      (t2 || s2.shouldBeIncluded(e3)) && s2.include(e3, t2);
  }
  render(e3, t2) {
    if (this.body.length > 0) {
      const s2 = un(e3.original.slice(this.start, this.end), "{") + 1;
      mn(this.body, e3, this.start + s2, this.end - 1, t2);
    } else
      super.render(e3, t2);
  }
}, Super: class extends Zs {
  bind() {
    this.variable = this.scope.findVariable("this");
  }
  deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2) {
    this.variable.deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2);
  }
  deoptimizePath(e3) {
    this.variable.deoptimizePath(e3);
  }
  include() {
    this.included || (this.included = true, this.context.includeVariableInModule(this.variable));
  }
}, SwitchCase: to, SwitchStatement: class extends Zs {
  createScope(e3) {
    this.parentScope = e3, this.scope = new xn(e3);
  }
  hasEffects(e3) {
    if (this.discriminant.hasEffects(e3))
      return true;
    const { brokenFlow: t2, hasBreak: s2, ignore: i2 } = e3, { breaks: n2 } = i2;
    i2.breaks = true, e3.hasBreak = false;
    let r2 = true;
    for (const s3 of this.cases) {
      if (s3.hasEffects(e3))
        return true;
      r2 && (r2 = e3.brokenFlow && !e3.hasBreak), e3.hasBreak = false, e3.brokenFlow = t2;
    }
    return null !== this.defaultCase && (e3.brokenFlow = r2), i2.breaks = n2, e3.hasBreak = s2, false;
  }
  include(e3, t2) {
    this.included = true, this.discriminant.include(e3, t2);
    const { brokenFlow: s2, hasBreak: i2 } = e3;
    e3.hasBreak = false;
    let n2 = true, r2 = t2 || null !== this.defaultCase && this.defaultCase < this.cases.length - 1;
    for (let i3 = this.cases.length - 1; i3 >= 0; i3--) {
      const o2 = this.cases[i3];
      if (o2.included && (r2 = true), !r2) {
        const e4 = ts();
        e4.ignore.breaks = true, r2 = o2.hasEffects(e4);
      }
      r2 ? (o2.include(e3, t2), n2 && (n2 = e3.brokenFlow && !e3.hasBreak), e3.hasBreak = false, e3.brokenFlow = s2) : n2 = s2;
    }
    r2 && null !== this.defaultCase && (e3.brokenFlow = n2), e3.hasBreak = i2;
  }
  initialise() {
    for (let e3 = 0; e3 < this.cases.length; e3++)
      if (null === this.cases[e3].test)
        return void (this.defaultCase = e3);
    this.defaultCase = null;
  }
  parseNode(e3) {
    this.discriminant = new (this.context.getNodeConstructor(e3.discriminant.type))(e3.discriminant, this, this.parentScope), super.parseNode(e3);
  }
  render(e3, t2) {
    this.discriminant.render(e3, t2), this.cases.length > 0 && mn(this.cases, e3, this.cases[0].start, this.end - 1, t2);
  }
}, TaggedTemplateExpression: class extends zn {
  bind() {
    if (super.bind(), this.tag.type === Rs) {
      const e3 = this.tag.name;
      this.scope.findVariable(e3).isNamespace && this.context.log(ve, Rt(e3), this.start);
    }
  }
  hasEffects(e3) {
    try {
      for (const t2 of this.quasi.expressions)
        if (t2.hasEffects(e3))
          return true;
      return this.tag.hasEffects(e3) || this.tag.hasEffectsOnInteractionAtPath(K, this.interaction, e3);
    } finally {
      this.deoptimized || this.applyDeoptimizations();
    }
  }
  include(e3, t2) {
    this.deoptimized || this.applyDeoptimizations(), t2 ? super.include(e3, t2) : (this.included = true, this.tag.include(e3, t2), this.quasi.include(e3, t2)), this.tag.includeCallArguments(e3, this.args);
    const [s2] = this.getReturnExpression();
    s2.included || s2.include(e3, false);
  }
  initialise() {
    this.args = [re, ...this.quasi.expressions], this.interaction = { args: [this.tag instanceof Vn && !this.tag.variable ? this.tag.object : null, ...this.args], type: 2, withNew: false };
  }
  render(e3, t2) {
    this.tag.render(e3, t2, { isCalleeOfRenderedParent: true }), this.quasi.render(e3, t2);
  }
  applyDeoptimizations() {
    this.deoptimized = true, this.tag.deoptimizeArgumentsOnInteractionAtPath(this.interaction, K, ee), this.context.requestTreeshakingPass();
  }
  getReturnExpression(e3 = ee) {
    return null === this.returnExpression ? (this.returnExpression = oe, this.returnExpression = this.tag.getReturnExpressionWhenCalledAtPath(K, this.interaction, e3, this)) : this.returnExpression;
  }
}, TemplateElement: class extends Zs {
  bind() {
  }
  hasEffects() {
    return false;
  }
  include() {
    this.included = true;
  }
  parseNode(e3) {
    this.value = e3.value, super.parseNode(e3);
  }
  render() {
  }
}, TemplateLiteral: so, ThisExpression: class extends Zs {
  bind() {
    this.variable = this.scope.findVariable("this");
  }
  deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2) {
    this.variable.deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2);
  }
  deoptimizePath(e3) {
    this.variable.deoptimizePath(e3);
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    return 0 === e3.length ? 0 !== t2.type : this.variable.hasEffectsOnInteractionAtPath(e3, t2, s2);
  }
  include() {
    this.included || (this.included = true, this.context.includeVariableInModule(this.variable));
  }
  initialise() {
    this.alias = this.scope.findLexicalBoundary() instanceof ro ? this.context.moduleContext : null, "undefined" === this.alias && this.context.log(ve, { code: "THIS_IS_UNDEFINED", message: "The 'this' keyword is equivalent to 'undefined' at the top level of an ES module, and has been rewritten", url: Oe("troubleshooting/#error-this-is-undefined") }, this.start);
  }
  render(e3) {
    null !== this.alias && e3.overwrite(this.start, this.end, this.alias, { contentOnly: false, storeName: true });
  }
}, ThrowStatement: class extends Zs {
  hasEffects() {
    return true;
  }
  include(e3, t2) {
    this.included = true, this.argument.include(e3, t2), e3.brokenFlow = true;
  }
  render(e3, t2) {
    this.argument.render(e3, t2, { preventASI: true }), this.argument.start === this.start + 5 && e3.prependLeft(this.start + 5, " ");
  }
}, TryStatement: class extends Zs {
  constructor() {
    super(...arguments), this.directlyIncluded = false, this.includedLabelsAfterBlock = null;
  }
  hasEffects(e3) {
    var _a2;
    return (this.context.options.treeshake.tryCatchDeoptimization ? this.block.body.length > 0 : this.block.hasEffects(e3)) || !!((_a2 = this.finalizer) == null ? void 0 : _a2.hasEffects(e3));
  }
  include(e3, t2) {
    var _a2, _b;
    const s2 = (_a2 = this.context.options.treeshake) == null ? void 0 : _a2.tryCatchDeoptimization, { brokenFlow: i2, includedLabels: n2 } = e3;
    if (this.directlyIncluded && s2) {
      if (this.includedLabelsAfterBlock)
        for (const e4 of this.includedLabelsAfterBlock)
          n2.add(e4);
    } else
      this.included = true, this.directlyIncluded = true, this.block.include(e3, s2 ? Qs : t2), n2.size > 0 && (this.includedLabelsAfterBlock = [...n2]), e3.brokenFlow = i2;
    null !== this.handler && (this.handler.include(e3, t2), e3.brokenFlow = i2), (_b = this.finalizer) == null ? void 0 : _b.include(e3, t2);
  }
}, UnaryExpression: class extends Zs {
  getLiteralValueAtPath(e3, t2, s2) {
    if (e3.length > 0)
      return se;
    const i2 = this.argument.getLiteralValueAtPath(K, t2, s2);
    return "symbol" == typeof i2 ? se : oo[this.operator](i2);
  }
  hasEffects(e3) {
    return this.deoptimized || this.applyDeoptimizations(), !("typeof" === this.operator && this.argument instanceof on) && (this.argument.hasEffects(e3) || "delete" === this.operator && this.argument.hasEffectsOnInteractionAtPath(K, he, e3));
  }
  hasEffectsOnInteractionAtPath(e3, { type: t2 }) {
    return 0 !== t2 || e3.length > ("void" === this.operator ? 0 : 1);
  }
  applyDeoptimizations() {
    this.deoptimized = true, "delete" === this.operator && (this.argument.deoptimizePath(K), this.context.requestTreeshakingPass());
  }
}, UnknownNode: class extends Zs {
  hasEffects() {
    return true;
  }
  include(e3) {
    super.include(e3, true);
  }
}, UpdateExpression: class extends Zs {
  hasEffects(e3) {
    return this.deoptimized || this.applyDeoptimizations(), this.argument.hasEffectsAsAssignmentTarget(e3, true);
  }
  hasEffectsOnInteractionAtPath(e3, { type: t2 }) {
    return e3.length > 1 || 0 !== t2;
  }
  include(e3, t2) {
    this.deoptimized || this.applyDeoptimizations(), this.included = true, this.argument.includeAsAssignmentTarget(e3, t2, true);
  }
  initialise() {
    this.argument.setAssignedValue(re);
  }
  render(e3, t2) {
    const { exportNamesByVariable: s2, format: i2, snippets: { _: n2 } } = t2;
    if (this.argument.render(e3, t2), "system" === i2) {
      const i3 = this.argument.variable, r2 = s2.get(i3);
      if (r2)
        if (this.prefix)
          1 === r2.length ? In(i3, this.start, this.end, e3, t2) : wn(i3, this.start, this.end, this.parent.type !== Ns, e3, t2);
        else {
          const s3 = this.operator[0];
          !function(e4, t3, s4, i4, n3, r3, o2) {
            const { _: a2 } = r3.snippets;
            n3.prependRight(t3, `${kn([e4], r3, o2)},${a2}`), i4 && (n3.prependRight(t3, "("), n3.appendLeft(s4, ")"));
          }(i3, this.start, this.end, this.parent.type !== Ns, e3, t2, `${n2}${s3}${n2}1`);
        }
    }
  }
  applyDeoptimizations() {
    if (this.deoptimized = true, this.argument.deoptimizePath(K), this.argument instanceof on) {
      this.scope.findVariable(this.argument.name).isReassigned = true;
    }
    this.context.requestTreeshakingPass();
  }
}, VariableDeclaration: ao, VariableDeclarator: Mr, WhileStatement: class extends Zs {
  hasEffects(e3) {
    return !!this.test.hasEffects(e3) || Qn(e3, this.body);
  }
  include(e3, t2) {
    this.included = true, this.test.include(e3, t2), Zn(e3, this.body, t2);
  }
}, YieldExpression: class extends Zs {
  hasEffects(e3) {
    var _a2;
    return this.deoptimized || this.applyDeoptimizations(), !(e3.ignore.returnYield && !((_a2 = this.argument) == null ? void 0 : _a2.hasEffects(e3)));
  }
  render(e3, t2) {
    this.argument && (this.argument.render(e3, t2, { preventASI: true }), this.argument.start === this.start + 5 && e3.prependLeft(this.start + 5, " "));
  }
} }, ho = "_missingExportShim";
class co extends ue {
  constructor(e3) {
    super(ho), this.module = e3;
  }
  include() {
    super.include(), this.module.needsExportShim = true;
  }
}
class uo extends ue {
  constructor(e3) {
    super(e3.getModuleName()), this.memberVariables = null, this.mergedNamespaces = [], this.referencedEarly = false, this.references = [], this.context = e3, this.module = e3.module;
  }
  addReference(e3) {
    this.references.push(e3), this.name = e3.name;
  }
  deoptimizeArgumentsOnInteractionAtPath(e3, t2, s2) {
    var _a2;
    if (t2.length > 1 || 1 === t2.length && 2 === e3.type) {
      const i2 = t2[0];
      "string" == typeof i2 ? (_a2 = this.getMemberVariables()[i2]) == null ? void 0 : _a2.deoptimizeArgumentsOnInteractionAtPath(e3, t2.slice(1), s2) : ae(e3);
    }
  }
  deoptimizePath(e3) {
    var _a2;
    if (e3.length > 1) {
      const t2 = e3[0];
      "string" == typeof t2 && ((_a2 = this.getMemberVariables()[t2]) == null ? void 0 : _a2.deoptimizePath(e3.slice(1)));
    }
  }
  getLiteralValueAtPath(e3) {
    return e3[0] === H ? "Module" : se;
  }
  getMemberVariables() {
    if (this.memberVariables)
      return this.memberVariables;
    const e3 = /* @__PURE__ */ Object.create(null), t2 = [...this.context.getExports(), ...this.context.getReexports()].sort();
    for (const s2 of t2)
      if ("*" !== s2[0] && s2 !== this.module.info.syntheticNamedExports) {
        const t3 = this.context.traceExport(s2);
        t3 && (e3[s2] = t3);
      }
    return this.memberVariables = e3;
  }
  hasEffectsOnInteractionAtPath(e3, t2, s2) {
    const { type: i2 } = t2;
    if (0 === e3.length)
      return true;
    if (1 === e3.length && 2 !== i2)
      return 1 === i2;
    const n2 = e3[0];
    if ("string" != typeof n2)
      return true;
    const r2 = this.getMemberVariables()[n2];
    return !r2 || r2.hasEffectsOnInteractionAtPath(e3.slice(1), t2, s2);
  }
  include() {
    this.included = true, this.context.includeAllExports();
  }
  prepare(e3) {
    this.mergedNamespaces.length > 0 && this.module.scope.addAccessedGlobals([mr], e3);
  }
  renderBlock(e3) {
    const { exportNamesByVariable: t2, format: s2, freeze: i2, indent: n2, namespaceToStringTag: r2, snippets: { _: o2, cnst: a2, getObject: l2, getPropertyAccess: h2, n: c2, s: u2 } } = e3, d2 = this.getMemberVariables(), p2 = Object.entries(d2).filter(([e4, t3]) => t3.included).map(([e4, t3]) => this.referencedEarly || t3.isReassigned || t3 === this ? [null, `get ${e4}${o2}()${o2}{${o2}return ${t3.getName(h2)}${u2}${o2}}`] : [e4, t3.getName(h2)]);
    p2.unshift([null, `__proto__:${o2}null`]);
    let f2 = l2(p2, { lineBreakIndent: { base: "", t: n2 } });
    if (this.mergedNamespaces.length > 0) {
      const e4 = this.mergedNamespaces.map((e5) => e5.getName(h2));
      f2 = `/*#__PURE__*/${mr}(${f2},${o2}[${e4.join(`,${o2}`)}])`;
    } else
      r2 && (f2 = `/*#__PURE__*/Object.defineProperty(${f2},${o2}Symbol.toStringTag,${o2}${Lr(l2)})`), i2 && (f2 = `/*#__PURE__*/Object.freeze(${f2})`);
    return f2 = `${a2} ${this.getName(h2)}${o2}=${o2}${f2};`, "system" === s2 && t2.has(this) && (f2 += `${c2}${kn([this], e3)};`), f2;
  }
  renderFirst() {
    return this.referencedEarly;
  }
  setMergedNamespaces(e3) {
    this.mergedNamespaces = e3;
    const t2 = this.context.getModuleExecIndex();
    for (const e4 of this.references)
      if (e4.context.getModuleExecIndex() <= t2) {
        this.referencedEarly = true;
        break;
      }
  }
}
uo.prototype.isNamespace = true;
class po extends ue {
  constructor(e3, t2, s2) {
    super(t2), this.baseVariable = null, this.context = e3, this.module = e3.module, this.syntheticNamespace = s2;
  }
  getBaseVariable() {
    if (this.baseVariable)
      return this.baseVariable;
    let e3 = this.syntheticNamespace;
    for (; e3 instanceof no || e3 instanceof po; ) {
      if (e3 instanceof no) {
        const t2 = e3.getOriginalVariable();
        if (t2 === e3)
          break;
        e3 = t2;
      }
      e3 instanceof po && (e3 = e3.syntheticNamespace);
    }
    return this.baseVariable = e3;
  }
  getBaseVariableName() {
    return this.syntheticNamespace.getBaseVariableName();
  }
  getName(e3) {
    return `${this.syntheticNamespace.getName(e3)}${e3(this.name)}`;
  }
  include() {
    this.included = true, this.context.includeVariableInModule(this.syntheticNamespace);
  }
  setRenderNames(e3, t2) {
    super.setRenderNames(e3, t2);
  }
}
var fo;
function mo(e3) {
  return e3.id;
}
!function(e3) {
  e3[e3.LOAD_AND_PARSE = 0] = "LOAD_AND_PARSE", e3[e3.ANALYSE = 1] = "ANALYSE", e3[e3.GENERATE = 2] = "GENERATE";
}(fo || (fo = {}));
const go = (e3) => {
  const t2 = e3.key;
  return t2 && (t2.name || t2.value);
};
function yo(e3, t2) {
  const s2 = Object.keys(e3);
  return s2.length !== Object.keys(t2).length || s2.some((s3) => e3[s3] !== t2[s3]);
}
var xo = "performance" in ("undefined" == typeof globalThis ? "undefined" == typeof window ? {} : window : globalThis) ? performance : { now: () => 0 }, Eo = { memoryUsage: () => ({ heapUsed: 0 }) };
let bo = /* @__PURE__ */ new Map();
function vo(e3, t2) {
  switch (t2) {
    case 1:
      return `# ${e3}`;
    case 2:
      return `## ${e3}`;
    case 3:
      return e3;
    default:
      return `${"  ".repeat(t2 - 4)}- ${e3}`;
  }
}
function So(e3, t2 = 3) {
  e3 = vo(e3, t2);
  const s2 = Eo.memoryUsage().heapUsed, i2 = xo.now(), n2 = bo.get(e3);
  void 0 === n2 ? bo.set(e3, { memory: 0, startMemory: s2, startTime: i2, time: 0, totalMemory: 0 }) : (n2.startMemory = s2, n2.startTime = i2);
}
function Ao(e3, t2 = 3) {
  e3 = vo(e3, t2);
  const s2 = bo.get(e3);
  if (void 0 !== s2) {
    const e4 = Eo.memoryUsage().heapUsed;
    s2.memory += e4 - s2.startMemory, s2.time += xo.now() - s2.startTime, s2.totalMemory = Math.max(s2.totalMemory, e4);
  }
}
function ko() {
  const e3 = {};
  for (const [t2, { memory: s2, time: i2, totalMemory: n2 }] of bo)
    e3[t2] = [i2, s2, n2];
  return e3;
}
let Io = Fi, wo = Fi;
const Po = ["augmentChunkHash", "buildEnd", "buildStart", "generateBundle", "load", "moduleParsed", "options", "outputOptions", "renderChunk", "renderDynamicImport", "renderStart", "resolveDynamicImport", "resolveFileUrl", "resolveId", "resolveImportMeta", "shouldTransformCachedModule", "transform", "writeBundle"];
function Co(e3, t2) {
  for (const s2 of Po)
    if (s2 in e3) {
      let i2 = `plugin ${t2}`;
      e3.name && (i2 += ` (${e3.name})`), i2 += ` - ${s2}`;
      const n2 = function(...e4) {
        Io(i2, 4);
        const t3 = r2.apply(this, e4);
        return wo(i2, 4), t3;
      };
      let r2;
      "function" == typeof e3[s2].handler ? (r2 = e3[s2].handler, e3[s2].handler = n2) : (r2 = e3[s2], e3[s2] = n2);
    }
  return e3;
}
function $o(e3) {
  e3.isExecuted = true;
  const t2 = [e3], s2 = /* @__PURE__ */ new Set();
  for (const e4 of t2)
    for (const i2 of [...e4.dependencies, ...e4.implicitlyLoadedBefore])
      i2 instanceof Qt || i2.isExecuted || !i2.info.moduleSideEffects && !e4.implicitlyLoadedBefore.has(i2) || s2.has(i2.id) || (i2.isExecuted = true, s2.add(i2.id), t2.push(i2));
}
const No = { identifier: null, localName: ho };
function _o(e3, t2, s2, i2, n2 = /* @__PURE__ */ new Map()) {
  const r2 = n2.get(t2);
  if (r2) {
    if (r2.has(e3))
      return i2 ? [null] : Ye((o2 = t2, a2 = e3.id, { code: it, exporter: a2, message: `"${o2}" cannot be exported from "${T(a2)}" as it is a reexport that references itself.` }));
    r2.add(e3);
  } else
    n2.set(t2, /* @__PURE__ */ new Set([e3]));
  var o2, a2;
  return e3.getVariableForExportName(t2, { importerForSideEffects: s2, isExportAllSearch: i2, searchedNamesAndModules: n2 });
}
function Ro(e3, t2) {
  const s2 = F(t2.sideEffectDependenciesByVariable, e3, j);
  let i2 = e3;
  const n2 = /* @__PURE__ */ new Set([i2]);
  for (; ; ) {
    const e4 = i2.module;
    if (i2 = i2 instanceof no ? i2.getDirectOriginalVariable() : i2 instanceof po ? i2.syntheticNamespace : null, !i2 || n2.has(i2))
      break;
    n2.add(i2), s2.add(e4);
    const t3 = e4.sideEffectDependenciesByVariable.get(i2);
    if (t3)
      for (const e5 of t3)
        s2.add(e5);
  }
  return s2;
}
class Oo {
  constructor(e3, t2, s2, i2, n2, r2, o2, a2) {
    this.graph = e3, this.id = t2, this.options = s2, this.alternativeReexportModules = /* @__PURE__ */ new Map(), this.chunkFileNames = /* @__PURE__ */ new Set(), this.chunkNames = [], this.cycles = /* @__PURE__ */ new Set(), this.dependencies = /* @__PURE__ */ new Set(), this.dynamicDependencies = /* @__PURE__ */ new Set(), this.dynamicImporters = [], this.dynamicImports = [], this.execIndex = 1 / 0, this.implicitlyLoadedAfter = /* @__PURE__ */ new Set(), this.implicitlyLoadedBefore = /* @__PURE__ */ new Set(), this.importDescriptions = /* @__PURE__ */ new Map(), this.importMetas = [], this.importedFromNotTreeshaken = false, this.importers = [], this.includedDynamicImporters = [], this.includedImports = /* @__PURE__ */ new Set(), this.isExecuted = false, this.isUserDefinedEntryPoint = false, this.needsExportShim = false, this.sideEffectDependenciesByVariable = /* @__PURE__ */ new Map(), this.sourcesWithAssertions = /* @__PURE__ */ new Map(), this.allExportNames = null, this.ast = null, this.exportAllModules = [], this.exportAllSources = /* @__PURE__ */ new Set(), this.exportNamesByVariable = null, this.exportShimVariable = new co(this), this.exports = /* @__PURE__ */ new Map(), this.namespaceReexportsByName = /* @__PURE__ */ new Map(), this.reexportDescriptions = /* @__PURE__ */ new Map(), this.relevantDependencies = null, this.syntheticExports = /* @__PURE__ */ new Map(), this.syntheticNamespace = null, this.transformDependencies = [], this.transitiveReexports = null, this.excludeFromSourcemap = /\0/.test(t2), this.context = s2.moduleContext(t2), this.preserveSignature = this.options.preserveEntrySignatures;
    const l2 = this, { dynamicImports: h2, dynamicImporters: c2, exportAllSources: u2, exports: d2, implicitlyLoadedAfter: p2, implicitlyLoadedBefore: f2, importers: m2, reexportDescriptions: g2, sourcesWithAssertions: y2 } = this;
    this.info = { assertions: a2, ast: null, code: null, get dynamicallyImportedIdResolutions() {
      return h2.map(({ argument: e4 }) => "string" == typeof e4 && l2.resolvedIds[e4]).filter(Boolean);
    }, get dynamicallyImportedIds() {
      return h2.map(({ id: e4 }) => e4).filter((e4) => null != e4);
    }, get dynamicImporters() {
      return c2.sort();
    }, get exportedBindings() {
      const e4 = { ".": [...d2.keys()] };
      for (const [t3, { source: s3 }] of g2)
        (e4[s3] ?? (e4[s3] = [])).push(t3);
      for (const t3 of u2)
        (e4[t3] ?? (e4[t3] = [])).push("*");
      return e4;
    }, get exports() {
      return [...d2.keys(), ...g2.keys(), ...[...u2].map(() => "*")];
    }, get hasDefaultExport() {
      return l2.ast ? l2.exports.has("default") || g2.has("default") : null;
    }, get hasModuleSideEffects() {
      return Yt("Accessing ModuleInfo.hasModuleSideEffects from plugins is deprecated. Please use ModuleInfo.moduleSideEffects instead.", Ke, true, s2), this.moduleSideEffects;
    }, id: t2, get implicitlyLoadedAfterOneOf() {
      return Array.from(p2, mo).sort();
    }, get implicitlyLoadedBefore() {
      return Array.from(f2, mo).sort();
    }, get importedIdResolutions() {
      return Array.from(y2.keys(), (e4) => l2.resolvedIds[e4]).filter(Boolean);
    }, get importedIds() {
      return Array.from(y2.keys(), (e4) => {
        var _a2;
        return (_a2 = l2.resolvedIds[e4]) == null ? void 0 : _a2.id;
      }).filter(Boolean);
    }, get importers() {
      return m2.sort();
    }, isEntry: i2, isExternal: false, get isIncluded() {
      return e3.phase !== fo.GENERATE ? null : l2.isIncluded();
    }, meta: { ...o2 }, moduleSideEffects: n2, syntheticNamedExports: r2 }, Object.defineProperty(this.info, "hasModuleSideEffects", { enumerable: false });
  }
  basename() {
    const e3 = w(this.id), t2 = C(this.id);
    return be(t2 ? e3.slice(0, -t2.length) : e3);
  }
  bindReferences() {
    this.ast.bind();
  }
  error(e3, t2) {
    return this.addLocationToLogProps(e3, t2), Ye(e3);
  }
  estimateSize() {
    let e3 = 0;
    for (const t2 of this.ast.body)
      t2.included && (e3 += t2.end - t2.start);
    return e3;
  }
  getAllExportNames() {
    if (this.allExportNames)
      return this.allExportNames;
    this.allExportNames = /* @__PURE__ */ new Set([...this.exports.keys(), ...this.reexportDescriptions.keys()]);
    for (const e3 of this.exportAllModules)
      if (e3 instanceof Qt)
        this.allExportNames.add(`*${e3.id}`);
      else
        for (const t2 of e3.getAllExportNames())
          "default" !== t2 && this.allExportNames.add(t2);
    return "string" == typeof this.info.syntheticNamedExports && this.allExportNames.delete(this.info.syntheticNamedExports), this.allExportNames;
  }
  getDependenciesToBeIncluded() {
    if (this.relevantDependencies)
      return this.relevantDependencies;
    this.relevantDependencies = /* @__PURE__ */ new Set();
    const e3 = /* @__PURE__ */ new Set(), t2 = /* @__PURE__ */ new Set(), s2 = new Set(this.includedImports);
    if (this.info.isEntry || this.includedDynamicImporters.length > 0 || this.namespace.included || this.implicitlyLoadedAfter.size > 0)
      for (const e4 of [...this.getReexports(), ...this.getExports()]) {
        const [t3] = this.getVariableForExportName(e4);
        (t3 == null ? void 0 : t3.included) && s2.add(t3);
      }
    for (let i2 of s2) {
      const s3 = this.sideEffectDependenciesByVariable.get(i2);
      if (s3)
        for (const e4 of s3)
          t2.add(e4);
      i2 instanceof po ? i2 = i2.getBaseVariable() : i2 instanceof no && (i2 = i2.getOriginalVariable()), e3.add(i2.module);
    }
    if (this.options.treeshake && "no-treeshake" !== this.info.moduleSideEffects)
      this.addRelevantSideEffectDependencies(this.relevantDependencies, e3, t2);
    else
      for (const e4 of this.dependencies)
        this.relevantDependencies.add(e4);
    for (const t3 of e3)
      this.relevantDependencies.add(t3);
    return this.relevantDependencies;
  }
  getExportNamesByVariable() {
    if (this.exportNamesByVariable)
      return this.exportNamesByVariable;
    const e3 = /* @__PURE__ */ new Map();
    for (const t2 of this.getAllExportNames()) {
      let [s2] = this.getVariableForExportName(t2);
      if (s2 instanceof no && (s2 = s2.getOriginalVariable()), !s2 || !(s2.included || s2 instanceof de))
        continue;
      const i2 = e3.get(s2);
      i2 ? i2.push(t2) : e3.set(s2, [t2]);
    }
    return this.exportNamesByVariable = e3;
  }
  getExports() {
    return [...this.exports.keys()];
  }
  getReexports() {
    if (this.transitiveReexports)
      return this.transitiveReexports;
    this.transitiveReexports = [];
    const e3 = new Set(this.reexportDescriptions.keys());
    for (const t2 of this.exportAllModules)
      if (t2 instanceof Qt)
        e3.add(`*${t2.id}`);
      else
        for (const s2 of [...t2.getReexports(), ...t2.getExports()])
          "default" !== s2 && e3.add(s2);
    return this.transitiveReexports = [...e3];
  }
  getRenderedExports() {
    const e3 = [], t2 = [];
    for (const s2 of this.exports.keys()) {
      const [i2] = this.getVariableForExportName(s2);
      (i2 && i2.included ? e3 : t2).push(s2);
    }
    return { removedExports: t2, renderedExports: e3 };
  }
  getSyntheticNamespace() {
    return null === this.syntheticNamespace && (this.syntheticNamespace = void 0, [this.syntheticNamespace] = this.getVariableForExportName("string" == typeof this.info.syntheticNamedExports ? this.info.syntheticNamedExports : "default", { onlyExplicit: true })), this.syntheticNamespace ? this.syntheticNamespace : Ye((e3 = this.id, t2 = this.info.syntheticNamedExports, { code: "SYNTHETIC_NAMED_EXPORTS_NEED_NAMESPACE_EXPORT", exporter: e3, message: `Module "${T(e3)}" that is marked with \`syntheticNamedExports: ${JSON.stringify(t2)}\` needs ${"string" == typeof t2 && "default" !== t2 ? `an explicit export named "${t2}"` : "a default export"} that does not reexport an unresolved named export of the same module.` }));
    var e3, t2;
  }
  getVariableForExportName(e3, { importerForSideEffects: t2, isExportAllSearch: s2, onlyExplicit: i2, searchedNamesAndModules: n2 } = fe) {
    if ("*" === e3[0]) {
      if (1 === e3.length)
        return [this.namespace];
      return this.graph.modulesById.get(e3.slice(1)).getVariableForExportName("*");
    }
    const r2 = this.reexportDescriptions.get(e3);
    if (r2) {
      const [e4] = _o(r2.module, r2.localName, t2, false, n2);
      return e4 ? (t2 && (Do(e4, t2, this), this.info.moduleSideEffects && F(t2.sideEffectDependenciesByVariable, e4, j).add(this)), [e4]) : this.error(Ft(r2.localName, this.id, r2.module.id), r2.start);
    }
    const o2 = this.exports.get(e3);
    if (o2) {
      if (o2 === No)
        return [this.exportShimVariable];
      const e4 = o2.localName, s3 = this.traceVariable(e4, { importerForSideEffects: t2, searchedNamesAndModules: n2 });
      return t2 && (Do(s3, t2, this), F(t2.sideEffectDependenciesByVariable, s3, j).add(this)), [s3];
    }
    if (i2)
      return [null];
    if ("default" !== e3) {
      const s3 = this.namespaceReexportsByName.get(e3) ?? this.getVariableFromNamespaceReexports(e3, t2, n2);
      if (this.namespaceReexportsByName.set(e3, s3), s3[0])
        return s3;
    }
    return this.info.syntheticNamedExports ? [F(this.syntheticExports, e3, () => new po(this.astContext, e3, this.getSyntheticNamespace()))] : !s2 && this.options.shimMissingExports ? (this.shimMissingExport(e3), [this.exportShimVariable]) : [null];
  }
  hasEffects() {
    return "no-treeshake" === this.info.moduleSideEffects || this.ast.hasCachedEffects();
  }
  include() {
    const e3 = es();
    this.ast.shouldBeIncluded(e3) && this.ast.include(e3, false);
  }
  includeAllExports(e3) {
    this.isExecuted || ($o(this), this.graph.needsTreeshakingPass = true);
    for (const t2 of this.exports.keys())
      if (e3 || t2 !== this.info.syntheticNamedExports) {
        const e4 = this.getVariableForExportName(t2)[0];
        e4.deoptimizePath(Y), e4.included || this.includeVariable(e4);
      }
    for (const e4 of this.getReexports()) {
      const [t2] = this.getVariableForExportName(e4);
      t2 && (t2.deoptimizePath(Y), t2.included || this.includeVariable(t2), t2 instanceof de && (t2.module.reexported = true));
    }
    e3 && this.namespace.setMergedNamespaces(this.includeAndGetAdditionalMergedNamespaces());
  }
  includeAllInBundle() {
    this.ast.include(es(), true), this.includeAllExports(false);
  }
  includeExportsByNames(e3) {
    this.isExecuted || ($o(this), this.graph.needsTreeshakingPass = true);
    let t2 = false;
    for (const s2 of e3) {
      const e4 = this.getVariableForExportName(s2)[0];
      e4 && (e4.deoptimizePath(Y), e4.included || this.includeVariable(e4)), this.exports.has(s2) || this.reexportDescriptions.has(s2) || (t2 = true);
    }
    t2 && this.namespace.setMergedNamespaces(this.includeAndGetAdditionalMergedNamespaces());
  }
  isIncluded() {
    return this.ast && (this.ast.included || this.namespace.included || this.importedFromNotTreeshaken || this.exportShimVariable.included);
  }
  linkImports() {
    this.addModulesToImportDescriptions(this.importDescriptions), this.addModulesToImportDescriptions(this.reexportDescriptions);
    const e3 = [];
    for (const t2 of this.exportAllSources) {
      const s2 = this.graph.modulesById.get(this.resolvedIds[t2].id);
      s2 instanceof Qt ? e3.push(s2) : this.exportAllModules.push(s2);
    }
    this.exportAllModules.push(...e3);
  }
  log(e3, t2, s2) {
    this.addLocationToLogProps(t2, s2), this.options.onLog(e3, t2);
  }
  render(e3) {
    const t2 = this.magicString.clone();
    this.ast.render(t2, e3), t2.trim();
    const { usesTopLevelAwait: s2 } = this.astContext;
    return s2 && "es" !== e3.format && "system" !== e3.format ? Ye((i2 = this.id, n2 = e3.format, { code: "INVALID_TLA_FORMAT", id: i2, message: `Module format "${n2}" does not support top-level await. Use the "es" or "system" output formats rather.` })) : { source: t2, usesTopLevelAwait: s2 };
    var i2, n2;
  }
  setSource({ ast: e3, code: t2, customTransformCache: s2, originalCode: i2, originalSourcemap: n2, resolvedIds: r2, sourcemapChain: o2, transformDependencies: a2, transformFiles: l2, ...h2 }) {
    Io("generate ast", 3), this.info.code = t2, this.originalCode = i2, this.originalSourcemap = n2, this.sourcemapChain = o2, l2 && (this.transformFiles = l2), this.transformDependencies = a2, this.customTransformCache = s2, this.updateOptions(h2);
    const c2 = e3 ?? this.tryParse();
    wo("generate ast", 3), Io("analyze ast", 3), this.resolvedIds = r2 ?? /* @__PURE__ */ Object.create(null);
    const u2 = this.id;
    this.magicString = new g(t2, { filename: this.excludeFromSourcemap ? null : u2, indentExclusionRanges: [] }), this.astContext = { addDynamicImport: this.addDynamicImport.bind(this), addExport: this.addExport.bind(this), addImport: this.addImport.bind(this), addImportMeta: this.addImportMeta.bind(this), code: t2, deoptimizationTracker: this.graph.deoptimizationTracker, error: this.error.bind(this), fileName: u2, getExports: this.getExports.bind(this), getModuleExecIndex: () => this.execIndex, getModuleName: this.basename.bind(this), getNodeConstructor: (e4) => lo[e4] || lo.UnknownNode, getReexports: this.getReexports.bind(this), importDescriptions: this.importDescriptions, includeAllExports: () => this.includeAllExports(true), includeDynamicImport: this.includeDynamicImport.bind(this), includeVariableInModule: this.includeVariableInModule.bind(this), log: this.log.bind(this), magicString: this.magicString, manualPureFunctions: this.graph.pureFunctions, module: this, moduleContext: this.context, options: this.options, requestTreeshakingPass: () => this.graph.needsTreeshakingPass = true, traceExport: (e4) => this.getVariableForExportName(e4)[0], traceVariable: this.traceVariable.bind(this), usesTopLevelAwait: false }, this.scope = new ro(this.graph.scope, this.astContext), this.namespace = new uo(this.astContext), this.ast = new eo(c2, { context: this.astContext, type: "Module" }, this.scope), e3 || false !== this.options.cache ? this.info.ast = c2 : Object.defineProperty(this.info, "ast", { get: () => {
      if (this.graph.astLru.has(u2))
        return this.graph.astLru.get(u2);
      {
        const e4 = this.tryParse();
        return this.graph.astLru.set(u2, e4), e4;
      }
    } }), wo("analyze ast", 3);
  }
  toJSON() {
    return { assertions: this.info.assertions, ast: this.info.ast, code: this.info.code, customTransformCache: this.customTransformCache, dependencies: Array.from(this.dependencies, mo), id: this.id, meta: this.info.meta, moduleSideEffects: this.info.moduleSideEffects, originalCode: this.originalCode, originalSourcemap: this.originalSourcemap, resolvedIds: this.resolvedIds, sourcemapChain: this.sourcemapChain, syntheticNamedExports: this.info.syntheticNamedExports, transformDependencies: this.transformDependencies, transformFiles: this.transformFiles };
  }
  traceVariable(e3, { importerForSideEffects: t2, isExportAllSearch: s2, searchedNamesAndModules: i2 } = fe) {
    const n2 = this.scope.variables.get(e3);
    if (n2)
      return n2;
    const r2 = this.importDescriptions.get(e3);
    if (r2) {
      const e4 = r2.module;
      if (e4 instanceof Oo && "*" === r2.name)
        return e4.namespace;
      const [n3] = _o(e4, r2.name, t2 || this, s2, i2);
      return n3 || this.error(Ft(r2.name, this.id, e4.id), r2.start);
    }
    return null;
  }
  updateOptions({ meta: e3, moduleSideEffects: t2, syntheticNamedExports: s2 }) {
    null != t2 && (this.info.moduleSideEffects = t2), null != s2 && (this.info.syntheticNamedExports = s2), null != e3 && Object.assign(this.info.meta, e3);
  }
  addDynamicImport(e3) {
    let t2 = e3.source;
    t2 instanceof so ? 1 === t2.quasis.length && t2.quasis[0].value.cooked && (t2 = t2.quasis[0].value.cooked) : t2 instanceof Ln && "string" == typeof t2.value && (t2 = t2.value), this.dynamicImports.push({ argument: t2, id: null, node: e3, resolution: null });
  }
  addExport(e3) {
    if (e3 instanceof tr)
      this.exports.set("default", { identifier: e3.variable.getAssignedVariableName(), localName: "default" });
    else if (e3 instanceof Jn) {
      const t2 = e3.source.value;
      if (this.addSource(t2, e3), e3.exported) {
        const s2 = e3.exported.name;
        this.reexportDescriptions.set(s2, { localName: "*", module: null, source: t2, start: e3.start });
      } else
        this.exportAllSources.add(t2);
    } else if (e3.source instanceof Ln) {
      const t2 = e3.source.value;
      this.addSource(t2, e3);
      for (const { exported: s2, local: i2, start: n2 } of e3.specifiers) {
        const e4 = s2 instanceof Ln ? s2.value : s2.name;
        this.reexportDescriptions.set(e4, { localName: i2 instanceof Ln ? i2.value : i2.name, module: null, source: t2, start: n2 });
      }
    } else if (e3.declaration) {
      const t2 = e3.declaration;
      if (t2 instanceof ao)
        for (const e4 of t2.declarations)
          for (const t3 of Jt(e4.id))
            this.exports.set(t3, { identifier: null, localName: t3 });
      else {
        const e4 = t2.id.name;
        this.exports.set(e4, { identifier: null, localName: e4 });
      }
    } else
      for (const { local: t2, exported: s2 } of e3.specifiers) {
        const e4 = t2.name, i2 = s2 instanceof on ? s2.name : s2.value;
        this.exports.set(i2, { identifier: null, localName: e4 });
      }
  }
  addImport(e3) {
    const t2 = e3.source.value;
    this.addSource(t2, e3);
    for (const s2 of e3.specifiers) {
      const e4 = s2 instanceof lr ? "default" : s2 instanceof Fr ? "*" : s2.imported instanceof on ? s2.imported.name : s2.imported.value;
      this.importDescriptions.set(s2.local.name, { module: null, name: e4, source: t2, start: s2.start });
    }
  }
  addImportMeta(e3) {
    this.importMetas.push(e3);
  }
  addLocationToLogProps(e3, t2) {
    e3.id = this.id, e3.pos = t2;
    let s2 = this.info.code;
    const i2 = we(s2, t2, { offsetLine: 1 });
    if (i2) {
      let { column: n2, line: r2 } = i2;
      try {
        ({ column: n2, line: r2 } = function(e4, t3) {
          const s3 = e4.filter((e5) => !!e5.mappings);
          e:
            for (; s3.length > 0; ) {
              const e5 = s3.pop().mappings[t3.line - 1];
              if (e5) {
                const s4 = e5.filter((e6) => e6.length > 1), i3 = s4[s4.length - 1];
                for (const e6 of s4)
                  if (e6[0] >= t3.column || e6 === i3) {
                    t3 = { column: e6[3], line: e6[2] + 1 };
                    continue e;
                  }
              }
              throw new Error("Can't resolve original location of error.");
            }
          return t3;
        }(this.sourcemapChain, { column: n2, line: r2 })), s2 = this.originalCode;
      } catch (e4) {
        this.options.onLog(ve, function(e5, t3, s3, i3, n3) {
          return { cause: e5, code: "SOURCEMAP_ERROR", id: t3, loc: { column: s3, file: t3, line: i3 }, message: `Error when using sourcemap for reporting an error: ${e5.message}`, pos: n3 };
        }(e4, this.id, n2, r2, t2));
      }
      Xe(e3, { column: n2, line: r2 }, s2, this.id);
    }
  }
  addModulesToImportDescriptions(e3) {
    for (const t2 of e3.values()) {
      const { id: e4 } = this.resolvedIds[t2.source];
      t2.module = this.graph.modulesById.get(e4);
    }
  }
  addRelevantSideEffectDependencies(e3, t2, s2) {
    const i2 = /* @__PURE__ */ new Set(), n2 = (r2) => {
      for (const o2 of r2)
        i2.has(o2) || (i2.add(o2), t2.has(o2) ? e3.add(o2) : (o2.info.moduleSideEffects || s2.has(o2)) && (o2 instanceof Qt || o2.hasEffects() ? e3.add(o2) : n2(o2.dependencies)));
    };
    n2(this.dependencies), n2(s2);
  }
  addSource(e3, t2) {
    const s2 = (i2 = t2.assertions, (i2 == null ? void 0 : i2.length) ? Object.fromEntries(i2.map((e4) => [go(e4), e4.value.value])) : fe);
    var i2;
    const n2 = this.sourcesWithAssertions.get(e3);
    n2 ? yo(n2, s2) && this.log(ve, Mt(n2, s2, e3, this.id), t2.start) : this.sourcesWithAssertions.set(e3, s2);
  }
  getVariableFromNamespaceReexports(e3, t2, s2) {
    let i2 = null;
    const n2 = /* @__PURE__ */ new Map(), r2 = /* @__PURE__ */ new Set();
    for (const o3 of this.exportAllModules) {
      if (o3.info.syntheticNamedExports === e3)
        continue;
      const [a3, l3] = _o(o3, e3, t2, true, Lo(s2));
      o3 instanceof Qt || l3 ? r2.add(a3) : a3 instanceof po ? i2 || (i2 = a3) : a3 && n2.set(a3, o3);
    }
    if (n2.size > 0) {
      const t3 = [...n2], s3 = t3[0][0];
      return 1 === t3.length ? [s3] : (this.options.onLog(ve, (o2 = e3, a2 = this.id, l2 = t3.map(([, e4]) => e4.id), { binding: o2, code: "NAMESPACE_CONFLICT", ids: l2, message: `Conflicting namespaces: "${T(a2)}" re-exports "${o2}" from one of the modules ${Re(l2.map((e4) => T(e4)))} (will be ignored).`, reexporter: a2 })), [null]);
    }
    var o2, a2, l2;
    if (r2.size > 0) {
      const t3 = [...r2], s3 = t3[0];
      return t3.length > 1 && this.options.onLog(ve, function(e4, t4, s4, i3) {
        return { binding: e4, code: "AMBIGUOUS_EXTERNAL_NAMESPACES", ids: i3, message: `Ambiguous external namespace resolution: "${T(t4)}" re-exports "${e4}" from one of the external modules ${Re(i3.map((e5) => T(e5)))}, guessing "${T(s4)}".`, reexporter: t4 };
      }(e3, this.id, s3.module.id, t3.map((e4) => e4.module.id))), [s3, true];
    }
    return i2 ? [i2] : [null];
  }
  includeAndGetAdditionalMergedNamespaces() {
    const e3 = /* @__PURE__ */ new Set(), t2 = /* @__PURE__ */ new Set();
    for (const s2 of [this, ...this.exportAllModules])
      if (s2 instanceof Qt) {
        const [t3] = s2.getVariableForExportName("*");
        t3.include(), this.includedImports.add(t3), e3.add(t3);
      } else if (s2.info.syntheticNamedExports) {
        const e4 = s2.getSyntheticNamespace();
        e4.include(), this.includedImports.add(e4), t2.add(e4);
      }
    return [...t2, ...e3];
  }
  includeDynamicImport(e3) {
    const t2 = this.dynamicImports.find((t3) => t3.node === e3).resolution;
    if (t2 instanceof Oo) {
      t2.includedDynamicImporters.push(this);
      const s2 = this.options.treeshake ? e3.getDeterministicImportedNames() : void 0;
      s2 ? t2.includeExportsByNames(s2) : t2.includeAllExports(true);
    }
  }
  includeVariable(e3) {
    const t2 = e3.module;
    if (e3.included)
      t2 instanceof Oo && t2 !== this && Ro(e3, this);
    else if (e3.include(), this.graph.needsTreeshakingPass = true, t2 instanceof Oo && (t2.isExecuted || $o(t2), t2 !== this)) {
      const t3 = Ro(e3, this);
      for (const e4 of t3)
        e4.isExecuted || $o(e4);
    }
  }
  includeVariableInModule(e3) {
    this.includeVariable(e3);
    const t2 = e3.module;
    t2 && t2 !== this && this.includedImports.add(e3);
  }
  shimMissingExport(e3) {
    var t2, s2;
    this.options.onLog(ve, (t2 = this.id, { binding: s2 = e3, code: "SHIMMED_EXPORT", exporter: t2, message: `Missing export "${s2}" has been shimmed in module "${T(t2)}".` })), this.exports.set(e3, No);
  }
  tryParse() {
    try {
      return this.graph.contextParse(this.info.code);
    } catch (e3) {
      return this.error(function(e4, t2) {
        let s2 = e4.message.replace(/ \(\d+:\d+\)$/, "");
        return t2.endsWith(".json") ? s2 += " (Note that you need @rollup/plugin-json to import JSON files)" : t2.endsWith(".js") || (s2 += " (Note that you need plugins to import files that are not JavaScript)"), { cause: e4, code: "PARSE_ERROR", id: t2, message: s2 };
      }(e3, this.id), e3.pos);
    }
  }
}
function Do(e3, t2, s2) {
  if (e3.module instanceof Oo && e3.module !== s2) {
    const i2 = e3.module.cycles;
    if (i2.size > 0) {
      const n2 = s2.cycles;
      for (const r2 of n2)
        if (i2.has(r2)) {
          t2.alternativeReexportModules.set(e3, s2);
          break;
        }
    }
  }
}
const Lo = (e3) => e3 && new Map(Array.from(e3, ([e4, t2]) => [e4, new Set(t2)]));
function To(e3) {
  return e3.endsWith(".js") ? e3.slice(0, -3) : e3;
}
function Mo(e3, t2) {
  return e3.autoId ? `${e3.basePath ? e3.basePath + "/" : ""}${To(t2)}` : e3.id ?? "";
}
function Vo(e3, t2, s2, i2, n2, r2, o2, a2 = "return ") {
  const { _: l2, getDirectReturnFunction: h2, getFunctionIntro: c2, getPropertyAccess: u2, n: d2, s: p2 } = n2;
  if (!s2)
    return `${d2}${d2}${a2}${function(e4, t3, s3, i3, n3) {
      if (e4.length > 0)
        return e4[0].local;
      for (const { defaultVariableName: e5, importPath: r3, isChunk: o3, name: a3, namedExportsMode: l3, namespaceVariableName: h3, reexports: c3 } of t3)
        if (c3)
          return Bo(a3, c3[0].imported, l3, o3, e5, h3, s3, r3, i3, n3);
    }(e3, t2, i2, o2, u2)};`;
  let f2 = "";
  for (const { defaultVariableName: e4, importPath: n3, isChunk: a3, name: c3, namedExportsMode: p3, namespaceVariableName: m2, reexports: g2 } of t2)
    if (g2 && s2) {
      for (const t3 of g2)
        if ("*" !== t3.reexported) {
          const s3 = Bo(c3, t3.imported, p3, a3, e4, m2, i2, n3, o2, u2);
          if (f2 && (f2 += d2), "*" !== t3.imported && t3.needsLiveBinding) {
            const [e5, i3] = h2([], { functionReturn: true, lineBreakIndent: null, name: null });
            f2 += `Object.defineProperty(exports,${l2}'${t3.reexported}',${l2}{${d2}${r2}enumerable:${l2}true,${d2}${r2}get:${l2}${e5}${s3}${i3}${d2}});`;
          } else
            f2 += `exports${u2(t3.reexported)}${l2}=${l2}${s3};`;
        }
    }
  for (const { exported: t3, local: s3 } of e3) {
    const e4 = `exports${u2(t3)}`;
    e4 !== s3 && (f2 && (f2 += d2), f2 += `${e4}${l2}=${l2}${s3};`);
  }
  for (const { name: e4, reexports: i3 } of t2)
    if (i3 && s2) {
      for (const t3 of i3)
        if ("*" === t3.reexported) {
          f2 && (f2 += d2);
          const s3 = `{${d2}${r2}if${l2}(k${l2}!==${l2}'default'${l2}&&${l2}!exports.hasOwnProperty(k))${l2}${jo(e4, t3.needsLiveBinding, r2, n2)}${p2}${d2}}`;
          f2 += `Object.keys(${e4}).forEach(${c2(["k"], { isAsync: false, name: null })}${s3});`;
        }
    }
  return f2 ? `${d2}${d2}${f2}` : "";
}
function Bo(e3, t2, s2, i2, n2, r2, o2, a2, l2, h2) {
  if ("default" === t2) {
    if (!i2) {
      const t3 = o2(a2), s3 = gr[t3] ? n2 : e3;
      return yr(t3, l2) ? `${s3}${h2("default")}` : s3;
    }
    return s2 ? `${e3}${h2("default")}` : e3;
  }
  return "*" === t2 ? (i2 ? !s2 : xr[o2(a2)]) ? r2 : e3 : `${e3}${h2(t2)}`;
}
function zo(e3) {
  return e3([["value", "true"]], { lineBreakIndent: null });
}
function Fo(e3, t2, s2, { _: i2, getObject: n2 }) {
  if (e3) {
    if (t2)
      return s2 ? `Object.defineProperties(exports,${i2}${n2([["__esModule", zo(n2)], [null, `[Symbol.toStringTag]:${i2}${Lr(n2)}`]], { lineBreakIndent: null })});` : `Object.defineProperty(exports,${i2}'__esModule',${i2}${zo(n2)});`;
    if (s2)
      return `Object.defineProperty(exports,${i2}Symbol.toStringTag,${i2}${Lr(n2)});`;
  }
  return "";
}
const jo = (e3, t2, s2, { _: i2, getDirectReturnFunction: n2, n: r2 }) => {
  if (t2) {
    const [t3, o2] = n2([], { functionReturn: true, lineBreakIndent: null, name: null });
    return `Object.defineProperty(exports,${i2}k,${i2}{${r2}${s2}${s2}enumerable:${i2}true,${r2}${s2}${s2}get:${i2}${t3}${e3}[k]${o2}${r2}${s2}})`;
  }
  return `exports[k]${i2}=${i2}${e3}[k]`;
};
function Uo(e3, t2, s2, i2, n2, r2, o2, a2) {
  const { _: l2, cnst: h2, n: c2 } = a2, u2 = /* @__PURE__ */ new Set(), d2 = [], p2 = (e4, t3, s3) => {
    u2.add(t3), d2.push(`${h2} ${e4}${l2}=${l2}/*#__PURE__*/${t3}(${s3});`);
  };
  for (const { defaultVariableName: s3, imports: i3, importPath: n3, isChunk: r3, name: o3, namedExportsMode: a3, namespaceVariableName: l3, reexports: h3 } of e3)
    if (r3) {
      for (const { imported: e4, reexported: t3 } of [...i3 || [], ...h3 || []])
        if ("*" === e4 && "*" !== t3) {
          a3 || p2(l3, fr, o3);
          break;
        }
    } else {
      const e4 = t2(n3);
      let r4 = false, a4 = false;
      for (const { imported: t3, reexported: n4 } of [...i3 || [], ...h3 || []]) {
        let i4, h4;
        "default" === t3 ? r4 || (r4 = true, s3 !== l3 && (h4 = s3, i4 = gr[e4])) : "*" !== t3 || "*" === n4 || a4 || (a4 = true, i4 = xr[e4], h4 = l3), i4 && p2(h4, i4, o3);
      }
    }
  return `${br(u2, r2, o2, a2, s2, i2, n2)}${d2.length > 0 ? `${d2.join(c2)}${c2}${c2}` : ""}`;
}
function Go(e3, t2) {
  return "." !== e3[0] ? e3 : t2 ? (s2 = e3).endsWith(".js") ? s2 : s2 + ".js" : To(e3);
  var s2;
}
const Wo = /* @__PURE__ */ new Set([...t(["assert", "async_hooks", "buffer", "child_process", "cluster", "console", "constants", "crypto", "dgram", "diagnostics_channel", "dns", "domain", "events", "fs", "http", "http2", "https", "inspector", "module", "net", "os", "path", "perf_hooks", "process", "punycode", "querystring", "readline", "repl", "stream", "string_decoder", "timers", "tls", "trace_events", "tty", "url", "util", "v8", "vm", "wasi", "worker_threads", "zlib"]), "assert/strict", "dns/promises", "fs/promises", "path/posix", "path/win32", "readline/promises", "stream/consumers", "stream/promises", "stream/web", "timers/promises", "util/types"]);
function qo(e3, t2) {
  const s2 = t2.map(({ importPath: e4 }) => e4).filter((e4) => Wo.has(e4) || e4.startsWith("node:"));
  0 !== s2.length && e3(ve, function(e4) {
    return { code: Et, ids: e4, message: `Creating a browser bundle that depends on Node.js built-in modules (${Re(e4)}). You might need to include https://github.com/FredKSchott/rollup-plugin-polyfill-node` };
  }(s2));
}
const Ho = (e3, t2) => e3.split(".").map(t2).join("");
function Ko(e3, t2, s2, i2, { _: n2, getPropertyAccess: r2 }) {
  const o2 = e3.split(".");
  o2[0] = ("function" == typeof s2 ? s2(o2[0]) : s2[o2[0]]) || o2[0];
  const a2 = o2.pop();
  let l2 = t2, h2 = [...o2.map((e4) => (l2 += r2(e4), `${l2}${n2}=${n2}${l2}${n2}||${n2}{}`)), `${l2}${r2(a2)}`].join(`,${n2}`) + `${n2}=${n2}${i2}`;
  return o2.length > 0 && (h2 = `(${h2})`), h2;
}
function Yo(e3) {
  let t2 = e3.length;
  for (; t2--; ) {
    const { imports: s2, reexports: i2 } = e3[t2];
    if (s2 || i2)
      return e3.slice(0, t2 + 1);
  }
  return [];
}
const Xo = ({ dependencies: e3, exports: t2 }) => {
  const s2 = new Set(t2.map((e4) => e4.exported));
  s2.add("default");
  for (const { reexports: t3 } of e3)
    if (t3)
      for (const e4 of t3)
        "*" !== e4.reexported && s2.add(e4.reexported);
  return s2;
}, Qo = (e3, t2, { _: s2, cnst: i2, getObject: n2, n: r2 }) => e3 ? `${r2}${t2}${i2} _starExcludes${s2}=${s2}${n2([...e3].map((e4) => [e4, "1"]), { lineBreakIndent: { base: t2, t: t2 } })};` : "", Zo = (e3, t2, { _: s2, n: i2 }) => e3.length > 0 ? `${i2}${t2}var ${e3.join(`,${s2}`)};` : "", Jo = (e3, t2, s2) => ea(e3.filter((e4) => e4.hoisted).map((e4) => ({ name: e4.exported, value: e4.local })), t2, s2);
function ea(e3, t2, { _: s2, n: i2 }) {
  return 0 === e3.length ? "" : 1 === e3.length ? `exports('${e3[0].name}',${s2}${e3[0].value});${i2}${i2}` : `exports({${i2}` + e3.map(({ name: e4, value: i3 }) => `${t2}${e4}:${s2}${i3}`).join(`,${i2}`) + `${i2}});${i2}${i2}`;
}
const ta = (e3, t2, s2) => ea(e3.filter((e4) => e4.expression).map((e4) => ({ name: e4.exported, value: e4.local })), t2, s2), sa = (e3, t2, s2) => ea(e3.filter((e4) => e4.local === ho).map((e4) => ({ name: e4.exported, value: ho })), t2, s2);
function ia(e3, t2, s2) {
  return e3 ? `${t2}${Ho(e3, s2)}` : "null";
}
var na = { amd: function(e3, { accessedGlobals: t2, dependencies: s2, exports: i2, hasDefaultExport: n2, hasExports: r2, id: o2, indent: a2, intro: l2, isEntryFacade: h2, isModuleFacade: c2, namedExportsMode: u2, log: d2, outro: p2, snippets: f2 }, { amd: m2, esModule: g2, externalLiveBindings: y2, freeze: x2, interop: E2, namespaceToStringTag: b2, strict: v2 }) {
  qo(d2, s2);
  const S2 = s2.map((e4) => `'${Go(e4.importPath, m2.forceJsExtensionForImports)}'`), A2 = s2.map((e4) => e4.name), { n: k2, getNonArrowFunctionIntro: I2, _: w2 } = f2;
  u2 && r2 && (A2.unshift("exports"), S2.unshift("'exports'")), t2.has("require") && (A2.unshift("require"), S2.unshift("'require'")), t2.has("module") && (A2.unshift("module"), S2.unshift("'module'"));
  const P2 = Mo(m2, o2), C2 = (P2 ? `'${P2}',${w2}` : "") + (S2.length > 0 ? `[${S2.join(`,${w2}`)}],${w2}` : ""), $2 = v2 ? `${w2}'use strict';` : "";
  e3.prepend(`${l2}${Uo(s2, E2, y2, x2, b2, t2, a2, f2)}`);
  const N2 = Vo(i2, s2, u2, E2, f2, a2, y2);
  let _2 = Fo(u2 && r2, h2 && (true === g2 || "if-default-prop" === g2 && n2), c2 && b2, f2);
  _2 && (_2 = k2 + k2 + _2), e3.append(`${N2}${_2}${p2}`).indent(a2).prepend(`${m2.define}(${C2}(${I2(A2, { isAsync: false, name: null })}{${$2}${k2}${k2}`).append(`${k2}${k2}}));`);
}, cjs: function(e3, { accessedGlobals: t2, dependencies: s2, exports: i2, hasDefaultExport: n2, hasExports: r2, indent: o2, intro: a2, isEntryFacade: l2, isModuleFacade: h2, namedExportsMode: c2, outro: u2, snippets: d2 }, { compact: p2, esModule: f2, externalLiveBindings: m2, freeze: g2, interop: y2, namespaceToStringTag: x2, strict: E2 }) {
  const { _: b2, n: v2 } = d2, S2 = E2 ? `'use strict';${v2}${v2}` : "";
  let A2 = Fo(c2 && r2, l2 && (true === f2 || "if-default-prop" === f2 && n2), h2 && x2, d2);
  A2 && (A2 += v2 + v2);
  const k2 = function(e4, { _: t3, cnst: s3, n: i3 }, n3) {
    let r3 = "", o3 = false;
    for (const { importPath: a3, name: l3, reexports: h3, imports: c3 } of e4)
      h3 || c3 ? (r3 += n3 && o3 ? "," : `${r3 ? `;${i3}` : ""}${s3} `, o3 = true, r3 += `${l3}${t3}=${t3}require('${a3}')`) : (r3 && (r3 += n3 && !o3 ? "," : `;${i3}`), o3 = false, r3 += `require('${a3}')`);
    if (r3)
      return `${r3};${i3}${i3}`;
    return "";
  }(s2, d2, p2), I2 = Uo(s2, y2, m2, g2, x2, t2, o2, d2);
  e3.prepend(`${S2}${a2}${A2}${k2}${I2}`);
  const w2 = Vo(i2, s2, c2, y2, d2, o2, m2, `module.exports${b2}=${b2}`);
  e3.append(`${w2}${u2}`);
}, es: function(e3, { accessedGlobals: t2, indent: s2, intro: i2, outro: n2, dependencies: r2, exports: o2, snippets: a2 }, { externalLiveBindings: l2, freeze: h2, namespaceToStringTag: c2 }) {
  const { n: u2 } = a2, d2 = function(e4, { _: t3 }) {
    const s3 = [];
    for (const { importPath: i3, reexports: n3, imports: r3, name: o3, assertions: a3 } of e4) {
      const e5 = `'${i3}'${a3 ? `${t3}assert${t3}${a3}` : ""};`;
      if (n3 || r3) {
        if (r3) {
          let i4 = null, n4 = null;
          const o4 = [];
          for (const e6 of r3)
            "default" === e6.imported ? i4 = e6 : "*" === e6.imported ? n4 = e6 : o4.push(e6);
          n4 && s3.push(`import${t3}*${t3}as ${n4.local} from${t3}${e5}`), i4 && 0 === o4.length ? s3.push(`import ${i4.local} from${t3}${e5}`) : o4.length > 0 && s3.push(`import ${i4 ? `${i4.local},${t3}` : ""}{${t3}${o4.map((e6) => e6.imported === e6.local ? e6.imported : `${e6.imported} as ${e6.local}`).join(`,${t3}`)}${t3}}${t3}from${t3}${e5}`);
        }
        if (n3) {
          let i4 = null;
          const a4 = [], l3 = [];
          for (const e6 of n3)
            "*" === e6.reexported ? i4 = e6 : "*" === e6.imported ? a4.push(e6) : l3.push(e6);
          if (i4 && s3.push(`export${t3}*${t3}from${t3}${e5}`), a4.length > 0) {
            r3 && r3.some((e6) => "*" === e6.imported && e6.local === o3) || s3.push(`import${t3}*${t3}as ${o3} from${t3}${e5}`);
            for (const e6 of a4)
              s3.push(`export${t3}{${t3}${o3 === e6.reexported ? o3 : `${o3} as ${e6.reexported}`} };`);
          }
          l3.length > 0 && s3.push(`export${t3}{${t3}${l3.map((e6) => e6.imported === e6.reexported ? e6.imported : `${e6.imported} as ${e6.reexported}`).join(`,${t3}`)}${t3}}${t3}from${t3}${e5}`);
        }
      } else
        s3.push(`import${t3}${e5}`);
    }
    return s3;
  }(r2, a2);
  d2.length > 0 && (i2 += d2.join(u2) + u2 + u2), (i2 += br(null, t2, s2, a2, l2, h2, c2)) && e3.prepend(i2);
  const p2 = function(e4, { _: t3, cnst: s3 }) {
    const i3 = [], n3 = [];
    for (const r3 of e4)
      r3.expression && i3.push(`${s3} ${r3.local}${t3}=${t3}${r3.expression};`), n3.push(r3.exported === r3.local ? r3.local : `${r3.local} as ${r3.exported}`);
    n3.length > 0 && i3.push(`export${t3}{${t3}${n3.join(`,${t3}`)}${t3}};`);
    return i3;
  }(o2, a2);
  p2.length > 0 && e3.append(u2 + u2 + p2.join(u2).trim()), n2 && e3.append(n2), e3.trim();
}, iife: function(e3, { accessedGlobals: t2, dependencies: s2, exports: i2, hasDefaultExport: n2, hasExports: r2, indent: o2, intro: a2, namedExportsMode: l2, log: h2, outro: c2, snippets: u2 }, { compact: d2, esModule: p2, extend: f2, freeze: m2, externalLiveBindings: g2, globals: y2, interop: x2, name: E2, namespaceToStringTag: b2, strict: v2 }) {
  const { _: S2, getNonArrowFunctionIntro: A2, getPropertyAccess: k2, n: I2 } = u2, w2 = E2 && E2.includes("."), P2 = !f2 && !w2;
  if (E2 && P2 && (Ee(C2 = E2) || xe.test(C2)))
    return Ye(function(e4) {
      return { code: at, message: `Given name "${e4}" is not a legal JS identifier. If you need this, you can try "output.extend: true".`, url: Oe(Be) };
    }(E2));
  var C2;
  qo(h2, s2);
  const $2 = Yo(s2), N2 = $2.map((e4) => e4.globalName || "null"), _2 = $2.map((e4) => e4.name);
  r2 && !E2 && h2(ve, { code: xt, message: 'If you do not supply "output.name", you may not be able to access the exports of an IIFE bundle.', url: Oe(qe) }), l2 && r2 && (f2 ? (N2.unshift(`this${Ho(E2, k2)}${S2}=${S2}this${Ho(E2, k2)}${S2}||${S2}{}`), _2.unshift("exports")) : (N2.unshift("{}"), _2.unshift("exports")));
  const R2 = v2 ? `${o2}'use strict';${I2}` : "", O2 = Uo(s2, x2, g2, m2, b2, t2, o2, u2);
  e3.prepend(`${a2}${O2}`);
  let D2 = `(${A2(_2, { isAsync: false, name: null })}{${I2}${R2}${I2}`;
  r2 && (!E2 || f2 && l2 || (D2 = (P2 ? `var ${E2}` : `this${Ho(E2, k2)}`) + `${S2}=${S2}${D2}`), w2 && (D2 = function(e4, t3, s3, { _: i3, getPropertyAccess: n3, s: r3 }, o3) {
    const a3 = e4.split(".");
    a3[0] = ("function" == typeof s3 ? s3(a3[0]) : s3[a3[0]]) || a3[0], a3.pop();
    let l3 = t3;
    return a3.map((e5) => (l3 += n3(e5), `${l3}${i3}=${i3}${l3}${i3}||${i3}{}${r3}`)).join(o3 ? "," : "\n") + (o3 && a3.length > 0 ? ";" : "\n");
  }(E2, "this", y2, u2, d2) + D2));
  let L2 = `${I2}${I2}})(${N2.join(`,${S2}`)});`;
  r2 && !f2 && l2 && (L2 = `${I2}${I2}${o2}return exports;${L2}`);
  const T2 = Vo(i2, s2, l2, x2, u2, o2, g2);
  let M2 = Fo(l2 && r2, true === p2 || "if-default-prop" === p2 && n2, b2, u2);
  M2 && (M2 = I2 + I2 + M2), e3.append(`${T2}${M2}${c2}`).indent(o2).prepend(D2).append(L2);
}, system: function(e3, { accessedGlobals: t2, dependencies: s2, exports: i2, hasExports: n2, indent: r2, intro: o2, snippets: a2, outro: l2, usesTopLevelAwait: h2 }, { externalLiveBindings: c2, freeze: u2, name: d2, namespaceToStringTag: p2, strict: f2, systemNullSetters: m2 }) {
  const { _: g2, getFunctionIntro: y2, getNonArrowFunctionIntro: x2, n: E2, s: b2 } = a2, { importBindings: v2, setters: S2, starExcludes: A2 } = function(e4, t3, s3, { _: i3, cnst: n3, getObject: r3, getPropertyAccess: o3, n: a3 }) {
    const l3 = [], h3 = [];
    let c3 = null;
    for (const { imports: u3, reexports: d3 } of e4) {
      const p3 = [];
      if (u3)
        for (const e5 of u3)
          l3.push(e5.local), "*" === e5.imported ? p3.push(`${e5.local}${i3}=${i3}module;`) : p3.push(`${e5.local}${i3}=${i3}module${o3(e5.imported)};`);
      if (d3) {
        const a4 = [];
        let l4 = false;
        for (const { imported: e5, reexported: t4 } of d3)
          "*" === t4 ? l4 = true : a4.push([t4, "*" === e5 ? "module" : `module${o3(e5)}`]);
        if (a4.length > 1 || l4) {
          const o4 = r3(a4, { lineBreakIndent: null });
          l4 ? (c3 || (c3 = Xo({ dependencies: e4, exports: t3 })), p3.push(`${n3} setter${i3}=${i3}${o4};`, `for${i3}(${n3} name in module)${i3}{`, `${s3}if${i3}(!_starExcludes[name])${i3}setter[name]${i3}=${i3}module[name];`, "}", "exports(setter);")) : p3.push(`exports(${o4});`);
        } else {
          const [e5, t4] = a4[0];
          p3.push(`exports('${e5}',${i3}${t4});`);
        }
      }
      h3.push(p3.join(`${a3}${s3}${s3}${s3}`));
    }
    return { importBindings: l3, setters: h3, starExcludes: c3 };
  }(s2, i2, r2, a2), k2 = d2 ? `'${d2}',${g2}` : "", I2 = t2.has("module") ? ["exports", "module"] : n2 ? ["exports"] : [];
  let w2 = `System.register(${k2}[` + s2.map(({ importPath: e4 }) => `'${e4}'`).join(`,${g2}`) + `],${g2}(${x2(I2, { isAsync: false, name: null })}{${E2}${r2}${f2 ? "'use strict';" : ""}` + Qo(A2, r2, a2) + Zo(v2, r2, a2) + `${E2}${r2}return${g2}{${S2.length > 0 ? `${E2}${r2}${r2}setters:${g2}[${S2.map((e4) => e4 ? `${y2(["module"], { isAsync: false, name: null })}{${E2}${r2}${r2}${r2}${e4}${E2}${r2}${r2}}` : m2 ? "null" : `${y2([], { isAsync: false, name: null })}{}`).join(`,${g2}`)}],` : ""}${E2}`;
  w2 += `${r2}${r2}execute:${g2}(${x2([], { isAsync: h2, name: null })}{${E2}${E2}`;
  const P2 = `${r2}${r2}})${E2}${r2}}${b2}${E2}}));`;
  e3.prepend(o2 + br(null, t2, r2, a2, c2, u2, p2) + Jo(i2, r2, a2)).append(`${l2}${E2}${E2}` + ta(i2, r2, a2) + sa(i2, r2, a2)).indent(`${r2}${r2}${r2}`).append(P2).prepend(w2);
}, umd: function(e3, { accessedGlobals: t2, dependencies: s2, exports: i2, hasDefaultExport: n2, hasExports: r2, id: o2, indent: a2, intro: l2, namedExportsMode: h2, log: c2, outro: u2, snippets: d2 }, { amd: p2, compact: f2, esModule: m2, extend: g2, externalLiveBindings: y2, freeze: x2, interop: E2, name: b2, namespaceToStringTag: v2, globals: S2, noConflict: A2, strict: k2 }) {
  const { _: I2, cnst: w2, getFunctionIntro: P2, getNonArrowFunctionIntro: C2, getPropertyAccess: $2, n: N2, s: _2 } = d2, R2 = f2 ? "f" : "factory", O2 = f2 ? "g" : "global";
  if (r2 && !b2)
    return Ye({ code: xt, message: 'You must supply "output.name" for UMD bundles that have exports so that the exports are accessible in environments without a module loader.', url: Oe(qe) });
  qo(c2, s2);
  const D2 = s2.map((e4) => `'${Go(e4.importPath, p2.forceJsExtensionForImports)}'`), L2 = s2.map((e4) => `require('${e4.importPath}')`), T2 = Yo(s2), M2 = T2.map((e4) => ia(e4.globalName, O2, $2)), V2 = T2.map((e4) => e4.name);
  h2 && (r2 || A2) && (D2.unshift("'exports'"), L2.unshift("exports"), M2.unshift(Ko(b2, O2, S2, (g2 ? `${ia(b2, O2, $2)}${I2}||${I2}` : "") + "{}", d2)), V2.unshift("exports"));
  const B2 = Mo(p2, o2), z2 = (B2 ? `'${B2}',${I2}` : "") + (D2.length > 0 ? `[${D2.join(`,${I2}`)}],${I2}` : ""), F2 = p2.define, j2 = !h2 && r2 ? `module.exports${I2}=${I2}` : "", U2 = k2 ? `${I2}'use strict';${N2}` : "";
  let G2;
  if (A2) {
    const e4 = f2 ? "e" : "exports";
    let t3;
    if (!h2 && r2)
      t3 = `${w2} ${e4}${I2}=${I2}${Ko(b2, O2, S2, `${R2}(${M2.join(`,${I2}`)})`, d2)};`;
    else {
      t3 = `${w2} ${e4}${I2}=${I2}${M2.shift()};${N2}${a2}${a2}${R2}(${[e4, ...M2].join(`,${I2}`)});`;
    }
    G2 = `(${P2([], { isAsync: false, name: null })}{${N2}${a2}${a2}${w2} current${I2}=${I2}${function(e5, t4, { _: s3, getPropertyAccess: i3 }) {
      let n3 = t4;
      return e5.split(".").map((e6) => n3 += i3(e6)).join(`${s3}&&${s3}`);
    }(b2, O2, d2)};${N2}${a2}${a2}${t3}${N2}${a2}${a2}${e4}.noConflict${I2}=${I2}${P2([], { isAsync: false, name: null })}{${I2}${ia(b2, O2, $2)}${I2}=${I2}current;${I2}return ${e4}${_2}${I2}};${N2}${a2}})()`;
  } else
    G2 = `${R2}(${M2.join(`,${I2}`)})`, !h2 && r2 && (G2 = Ko(b2, O2, S2, G2, d2));
  const W2 = r2 || A2 && h2 || M2.length > 0, q2 = [R2];
  W2 && q2.unshift(O2);
  const H2 = W2 ? `this,${I2}` : "", K2 = W2 ? `(${O2}${I2}=${I2}typeof globalThis${I2}!==${I2}'undefined'${I2}?${I2}globalThis${I2}:${I2}${O2}${I2}||${I2}self,${I2}` : "", Y2 = W2 ? ")" : "", X2 = W2 ? `${a2}typeof exports${I2}===${I2}'object'${I2}&&${I2}typeof module${I2}!==${I2}'undefined'${I2}?${I2}${j2}${R2}(${L2.join(`,${I2}`)})${I2}:${N2}` : "", Q2 = `(${C2(q2, { isAsync: false, name: null })}{${N2}` + X2 + `${a2}typeof ${F2}${I2}===${I2}'function'${I2}&&${I2}${F2}.amd${I2}?${I2}${F2}(${z2}${R2})${I2}:${N2}${a2}${K2}${G2}${Y2};${N2}})(${H2}(${C2(V2, { isAsync: false, name: null })}{${U2}${N2}`, Z2 = N2 + N2 + "}));";
  e3.prepend(`${l2}${Uo(s2, E2, y2, x2, v2, t2, a2, d2)}`);
  const J2 = Vo(i2, s2, h2, E2, d2, a2, y2);
  let ee2 = Fo(h2 && r2, true === m2 || "if-default-prop" === m2 && n2, v2, d2);
  ee2 && (ee2 = N2 + N2 + ee2), e3.append(`${J2}${ee2}${u2}`).trim().indent(a2).append(Z2).prepend(Q2);
} };
const ra = (e3, t2) => t2 ? `${e3}
${t2}` : e3, oa = (e3, t2) => t2 ? `${e3}

${t2}` : e3;
async function aa(e3, t2, s2) {
  try {
    let [i3, n3, r3, o2] = await Promise.all([t2.hookReduceValue("banner", e3.banner(s2), [s2], ra), t2.hookReduceValue("footer", e3.footer(s2), [s2], ra), t2.hookReduceValue("intro", e3.intro(s2), [s2], oa), t2.hookReduceValue("outro", e3.outro(s2), [s2], oa)]);
    return r3 && (r3 += "\n\n"), o2 && (o2 = `

${o2}`), i3 && (i3 += "\n"), n3 && (n3 = "\n" + n3), { banner: i3, footer: n3, intro: r3, outro: o2 };
  } catch (e4) {
    return Ye((i2 = e4.message, n2 = e4.hook, r2 = e4.plugin, { code: Qe, message: `Could not retrieve "${n2}". Check configuration of plugin "${r2}".
	Error Message: ${i2}` }));
  }
  var i2, n2, r2;
}
const la = { amd: ua, cjs: ua, es: ca, iife: ua, system: ca, umd: ua };
function ha(e3, t2, s2, i2, n2, r2, o2, a2, l2, h2, c2, u2, d2, p2) {
  const f2 = [...e3].reverse();
  for (const e4 of f2)
    e4.scope.addUsedOutsideNames(i2, n2, u2, d2);
  !function(e4, t3, s3) {
    for (const i3 of t3) {
      for (const t4 of i3.scope.variables.values())
        t4.included && !(t4.renderBaseName || t4 instanceof no && t4.getOriginalVariable() !== t4) && t4.setRenderNames(null, Di(t4.name, e4, t4.forbiddenNames));
      if (s3.has(i3)) {
        const t4 = i3.namespace;
        t4.setRenderNames(null, Di(t4.name, e4, t4.forbiddenNames));
      }
    }
  }(i2, f2, p2), la[n2](i2, s2, t2, r2, o2, a2, l2, h2, c2);
  for (const e4 of f2)
    e4.scope.deconflict(n2, u2, d2);
}
function ca(e3, t2, s2, i2, n2, r2, o2, a2, l2) {
  for (const t3 of s2.dependencies)
    (n2 || t3 instanceof z) && (t3.variableName = Di(t3.suggestedVariableName, e3, null));
  for (const s3 of t2) {
    const t3 = s3.module, i3 = s3.name;
    s3.isNamespace && (n2 || t3 instanceof Qt) ? s3.setRenderNames(null, (t3 instanceof Qt ? a2.get(t3) : o2.get(t3)).variableName) : t3 instanceof Qt && "default" === i3 ? s3.setRenderNames(null, Di([...t3.exportedVariables].some(([e4, t4]) => "*" === t4 && e4.included) ? t3.suggestedVariableName + "__default" : t3.suggestedVariableName, e3, s3.forbiddenNames)) : s3.setRenderNames(null, Di(i3, e3, s3.forbiddenNames));
  }
  for (const t3 of l2)
    t3.setRenderNames(null, Di(t3.name, e3, t3.forbiddenNames));
}
function ua(e3, t2, { deconflictedDefault: s2, deconflictedNamespace: i2, dependencies: n2 }, r2, o2, a2, l2, h2) {
  for (const t3 of n2)
    t3.variableName = Di(t3.suggestedVariableName, e3, null);
  for (const t3 of i2)
    t3.namespaceVariableName = Di(`${t3.suggestedVariableName}__namespace`, e3, null);
  for (const t3 of s2)
    t3.defaultVariableName = i2.has(t3) && Er(r2(t3.id), a2) ? t3.namespaceVariableName : Di(`${t3.suggestedVariableName}__default`, e3, null);
  for (const e4 of t2) {
    const t3 = e4.module;
    if (t3 instanceof Qt) {
      const s3 = h2.get(t3), i3 = e4.name;
      if ("default" === i3) {
        const i4 = r2(t3.id), n3 = gr[i4] ? s3.defaultVariableName : s3.variableName;
        yr(i4, a2) ? e4.setRenderNames(n3, "default") : e4.setRenderNames(null, n3);
      } else
        "*" === i3 ? e4.setRenderNames(null, xr[r2(t3.id)] ? s3.namespaceVariableName : s3.variableName) : e4.setRenderNames(s3.variableName, null);
    } else {
      const s3 = l2.get(t3);
      o2 && e4.isNamespace ? e4.setRenderNames(null, "default" === s3.exportMode ? s3.namespaceVariableName : s3.variableName) : "default" === s3.exportMode ? e4.setRenderNames(null, s3.variableName) : e4.setRenderNames(s3.variableName, s3.getVariableExportName(e4));
    }
  }
}
function da(e3, { exports: t2, name: s2, format: i2 }, n2, r2) {
  const o2 = e3.getExportNames();
  if ("default" === t2) {
    if (1 !== o2.length || "default" !== o2[0])
      return Ye(Bt("default", o2, n2));
  } else if ("none" === t2 && o2.length > 0)
    return Ye(Bt("none", o2, n2));
  return "auto" === t2 && (0 === o2.length ? t2 = "none" : 1 === o2.length && "default" === o2[0] ? t2 = "default" : ("es" !== i2 && "system" !== i2 && o2.includes("default") && r2(ve, function(e4, t3) {
    return { code: vt, id: e4, message: `Entry module "${T(e4)}" is using named and default exports together. Consumers of your bundle will have to use \`${t3 || "chunk"}.default\` to access the default export, which may not be what you want. Use \`output.exports: "named"\` to disable this warning.`, url: Oe(Ve) };
  }(n2, s2)), t2 = "named")), t2;
}
function pa(e3) {
  const t2 = e3.split("\n"), s2 = t2.filter((e4) => /^\t+/.test(e4)), i2 = t2.filter((e4) => /^ {2,}/.test(e4));
  if (0 === s2.length && 0 === i2.length)
    return null;
  if (s2.length >= i2.length)
    return "	";
  const n2 = i2.reduce((e4, t3) => {
    const s3 = /^ +/.exec(t3)[0].length;
    return Math.min(s3, e4);
  }, 1 / 0);
  return " ".repeat(n2);
}
function fa(e3, t2, s2, i2, n2, r2) {
  const o2 = e3.getDependenciesToBeIncluded();
  for (const e4 of o2) {
    if (e4 instanceof Qt) {
      t2.push(r2.get(e4));
      continue;
    }
    const o3 = n2.get(e4);
    o3 === i2 ? s2.has(e4) || (s2.add(e4), fa(e4, t2, s2, i2, n2, r2)) : t2.push(o3);
  }
}
const ma = "!~{", ga = "}~", ya = new RegExp(`${ma}[0-9a-zA-Z_$]{1,59}${ga}`, "g"), xa = (e3, t2) => e3.replace(ya, (e4) => t2.get(e4) || e4), Ea = (e3, t2, s2) => e3.replace(ya, (e4) => e4 === t2 ? s2 : e4), ba = (e3, t2) => {
  const s2 = /* @__PURE__ */ new Set(), i2 = e3.replace(ya, (e4) => t2.has(e4) ? (s2.add(e4), `${ma}${"0".repeat(e4.length - 5)}${ga}`) : e4);
  return { containedPlaceholders: s2, transformedCode: i2 };
}, va = Symbol("bundleKeys"), Sa = { type: "placeholder" };
function Aa(e3, t2, s2) {
  return M(e3) ? Ye(Kt(`Invalid pattern "${e3}" for "${t2}", patterns can be neither absolute nor relative paths. If you want your files to be stored in a subdirectory, write its name without a leading slash like this: subdirectory/pattern.`)) : e3.replace(/\[(\w+)(:\d+)?]/g, (e4, i2, n2) => {
    if (!s2.hasOwnProperty(i2) || n2 && "hash" !== i2)
      return Ye(Kt(`"[${i2}${n2 || ""}]" is not a valid placeholder in the "${t2}" pattern.`));
    const r2 = s2[i2](n2 && Number.parseInt(n2.slice(1)));
    return M(r2) ? Ye(Kt(`Invalid substitution "${r2}" for placeholder "[${i2}]" in "${t2}" pattern, can be neither absolute nor relative path.`)) : r2;
  });
}
function ka(e3, { [va]: t2 }) {
  if (!t2.has(e3.toLowerCase()))
    return e3;
  const s2 = C(e3);
  e3 = e3.slice(0, Math.max(0, e3.length - s2.length));
  let i2, n2 = 1;
  for (; t2.has((i2 = e3 + ++n2 + s2).toLowerCase()); )
    ;
  return i2;
}
const Ia = /* @__PURE__ */ new Set([".js", ".jsx", ".ts", ".tsx", ".mjs", ".mts", ".cjs", ".cts"]);
function wa(e3, t2, s2, i2) {
  const n2 = "function" == typeof t2 ? t2(e3.id) : t2[e3.id];
  return n2 || (s2 ? (i2(ve, (r2 = e3.id, o2 = e3.variableName, { code: gt, id: r2, message: `No name was provided for external module "${r2}" in "output.globals" – guessing "${o2}".`, names: [o2], url: Oe(je) })), e3.variableName) : void 0);
  var r2, o2;
}
class Pa {
  constructor(e3, t2, s2, i2, n2, r2, o2, a2, l2, h2, c2, u2, d2, p2, f2) {
    this.orderedModules = e3, this.inputOptions = t2, this.outputOptions = s2, this.unsetOptions = i2, this.pluginDriver = n2, this.modulesById = r2, this.chunkByModule = o2, this.externalChunkByModule = a2, this.facadeChunkByModule = l2, this.includedNamespaces = h2, this.manualChunkAlias = c2, this.getPlaceholder = u2, this.bundle = d2, this.inputBase = p2, this.snippets = f2, this.entryModules = [], this.exportMode = "named", this.facadeModule = null, this.namespaceVariableName = "", this.variableName = "", this.accessedGlobalsByScope = /* @__PURE__ */ new Map(), this.dependencies = /* @__PURE__ */ new Set(), this.dynamicEntryModules = [], this.dynamicName = null, this.exportNamesByVariable = /* @__PURE__ */ new Map(), this.exports = /* @__PURE__ */ new Set(), this.exportsByName = /* @__PURE__ */ new Map(), this.fileName = null, this.implicitEntryModules = [], this.implicitlyLoadedBefore = /* @__PURE__ */ new Set(), this.imports = /* @__PURE__ */ new Set(), this.includedDynamicImports = null, this.includedReexportsByModule = /* @__PURE__ */ new Map(), this.isEmpty = true, this.name = null, this.needsExportsShim = false, this.preRenderedChunkInfo = null, this.preliminaryFileName = null, this.renderedChunkInfo = null, this.renderedDependencies = null, this.renderedModules = /* @__PURE__ */ Object.create(null), this.sortedExportNames = null, this.strictFacade = false, this.execIndex = e3.length > 0 ? e3[0].execIndex : 1 / 0;
    const m2 = new Set(e3);
    for (const t3 of e3) {
      o2.set(t3, this), t3.namespace.included && !s2.preserveModules && h2.add(t3), this.isEmpty && t3.isIncluded() && (this.isEmpty = false), (t3.info.isEntry || s2.preserveModules) && this.entryModules.push(t3);
      for (const e4 of t3.includedDynamicImporters)
        m2.has(e4) || (this.dynamicEntryModules.push(t3), t3.info.syntheticNamedExports && (h2.add(t3), this.exports.add(t3.namespace)));
      t3.implicitlyLoadedAfter.size > 0 && this.implicitEntryModules.push(t3);
    }
    this.suggestedVariableName = be(this.generateVariableName());
  }
  static generateFacade(e3, t2, s2, i2, n2, r2, o2, a2, l2, h2, c2, u2, d2, p2, f2) {
    const m2 = new Pa([], e3, t2, s2, i2, n2, r2, o2, a2, l2, null, u2, d2, p2, f2);
    m2.assignFacadeName(c2, h2), a2.has(h2) || a2.set(h2, m2);
    for (const e4 of h2.getDependenciesToBeIncluded())
      m2.dependencies.add(e4 instanceof Oo ? r2.get(e4) : o2.get(e4));
    return !m2.dependencies.has(r2.get(h2)) && h2.info.moduleSideEffects && h2.hasEffects() && m2.dependencies.add(r2.get(h2)), m2.ensureReexportsAreAvailableForModule(h2), m2.facadeModule = h2, m2.strictFacade = true, m2;
  }
  canModuleBeFacade(e3, t2) {
    const s2 = e3.getExportNamesByVariable();
    for (const e4 of this.exports)
      if (!s2.has(e4))
        return false;
    for (const i2 of t2)
      if (!(i2.module === e3 || s2.has(i2) || i2 instanceof po && s2.has(i2.getBaseVariable())))
        return false;
    return true;
  }
  finalizeChunk(e3, t2, s2) {
    const i2 = this.getRenderedChunkInfo(), n2 = (e4) => xa(e4, s2), r2 = this.fileName = n2(i2.fileName);
    return { ...i2, code: e3, dynamicImports: i2.dynamicImports.map(n2), fileName: r2, implicitlyLoadedBefore: i2.implicitlyLoadedBefore.map(n2), importedBindings: Object.fromEntries(Object.entries(i2.importedBindings).map(([e4, t3]) => [n2(e4), t3])), imports: i2.imports.map(n2), map: t2, referencedFiles: i2.referencedFiles.map(n2) };
  }
  generateExports() {
    this.sortedExportNames = null;
    const e3 = new Set(this.exports);
    if (null !== this.facadeModule && (false !== this.facadeModule.preserveSignature || this.strictFacade)) {
      const t2 = this.facadeModule.getExportNamesByVariable();
      for (const [s2, i2] of t2) {
        this.exportNamesByVariable.set(s2, [...i2]);
        for (const e4 of i2)
          this.exportsByName.set(e4, s2);
        e3.delete(s2);
      }
    }
    this.outputOptions.minifyInternalExports ? function(e4, t2, s2) {
      let i2 = 0;
      for (const n2 of e4) {
        let [e5] = n2.name;
        if (t2.has(e5))
          do {
            e5 = Oi(++i2), 49 === e5.charCodeAt(0) && (i2 += 9 * 64 ** (e5.length - 1), e5 = Oi(i2));
          } while (ye.has(e5) || t2.has(e5));
        t2.set(e5, n2), s2.set(n2, [e5]);
      }
    }(e3, this.exportsByName, this.exportNamesByVariable) : function(e4, t2, s2) {
      for (const i2 of e4) {
        let e5 = 0, n2 = i2.name;
        for (; t2.has(n2); )
          n2 = i2.name + "$" + ++e5;
        t2.set(n2, i2), s2.set(i2, [n2]);
      }
    }(e3, this.exportsByName, this.exportNamesByVariable), (this.outputOptions.preserveModules || this.facadeModule && this.facadeModule.info.isEntry) && (this.exportMode = da(this, this.outputOptions, this.facadeModule.id, this.inputOptions.onLog));
  }
  generateFacades() {
    var _a2;
    const e3 = [], t2 = /* @__PURE__ */ new Set([...this.entryModules, ...this.implicitEntryModules]), s2 = new Set(this.dynamicEntryModules.map(({ namespace: e4 }) => e4));
    for (const e4 of t2)
      if (e4.preserveSignature)
        for (const t3 of e4.getExportNamesByVariable().keys())
          this.chunkByModule.get(t3.module) === this && s2.add(t3);
    for (const i2 of t2) {
      const t3 = Array.from(new Set(i2.chunkNames.filter(({ isUserDefined: e4 }) => e4).map(({ name: e4 }) => e4)), (e4) => ({ name: e4 }));
      if (0 === t3.length && i2.isUserDefinedEntryPoint && t3.push({}), t3.push(...Array.from(i2.chunkFileNames, (e4) => ({ fileName: e4 }))), 0 === t3.length && t3.push({}), !this.facadeModule) {
        const e4 = !this.outputOptions.preserveModules && ("strict" === i2.preserveSignature || "exports-only" === i2.preserveSignature && i2.getExportNamesByVariable().size > 0);
        e4 && !this.canModuleBeFacade(i2, s2) || (this.facadeModule = i2, this.facadeChunkByModule.set(i2, this), i2.preserveSignature && (this.strictFacade = e4), this.assignFacadeName(t3.shift(), i2, this.outputOptions.preserveModules));
      }
      for (const s3 of t3)
        e3.push(Pa.generateFacade(this.inputOptions, this.outputOptions, this.unsetOptions, this.pluginDriver, this.modulesById, this.chunkByModule, this.externalChunkByModule, this.facadeChunkByModule, this.includedNamespaces, i2, s3, this.getPlaceholder, this.bundle, this.inputBase, this.snippets));
    }
    for (const e4 of this.dynamicEntryModules)
      e4.info.syntheticNamedExports || (!this.facadeModule && this.canModuleBeFacade(e4, s2) ? (this.facadeModule = e4, this.facadeChunkByModule.set(e4, this), this.strictFacade = true, this.dynamicName = Ca(e4)) : this.facadeModule === e4 && !this.strictFacade && this.canModuleBeFacade(e4, s2) ? this.strictFacade = true : ((_a2 = this.facadeChunkByModule.get(e4)) == null ? void 0 : _a2.strictFacade) || (this.includedNamespaces.add(e4), this.exports.add(e4.namespace)));
    return this.outputOptions.preserveModules || this.addNecessaryImportsForFacades(), e3;
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
  getImportPath(e3) {
    return D(B(e3, this.getFileName(), "amd" === this.outputOptions.format && !this.outputOptions.amd.forceJsExtensionForImports, true));
  }
  getPreliminaryFileName() {
    var _a2;
    if (this.preliminaryFileName)
      return this.preliminaryFileName;
    let e3, t2 = null;
    const { chunkFileNames: s2, entryFileNames: i2, file: n2, format: r2, preserveModules: o2 } = this.outputOptions;
    if (n2)
      e3 = w(n2);
    else if (null === this.fileName) {
      const [n3, a2] = o2 || ((_a2 = this.facadeModule) == null ? void 0 : _a2.isUserDefinedEntryPoint) ? [i2, "output.entryFileNames"] : [s2, "output.chunkFileNames"];
      e3 = Aa("function" == typeof n3 ? n3(this.getPreRenderedChunkInfo()) : n3, a2, { format: () => r2, hash: (e4) => t2 || (t2 = this.getPlaceholder(a2, e4)), name: () => this.getChunkName() }), t2 || (e3 = ka(e3, this.bundle));
    } else
      e3 = this.fileName;
    return t2 || (this.bundle[e3] = Sa), this.preliminaryFileName = { fileName: e3, hashPlaceholder: t2 };
  }
  getRenderedChunkInfo() {
    return this.renderedChunkInfo ? this.renderedChunkInfo : this.renderedChunkInfo = { ...this.getPreRenderedChunkInfo(), dynamicImports: this.getDynamicDependencies().map(Ra), fileName: this.getFileName(), implicitlyLoadedBefore: Array.from(this.implicitlyLoadedBefore, Ra), importedBindings: Na(this.getRenderedDependencies(), Ra), imports: Array.from(this.dependencies, Ra), modules: this.renderedModules, referencedFiles: this.getReferencedFiles() };
  }
  getVariableExportName(e3) {
    return this.outputOptions.preserveModules && e3 instanceof uo ? "*" : this.exportNamesByVariable.get(e3)[0];
  }
  link() {
    this.dependencies = function(e3, t2, s2, i2) {
      const n2 = [], r2 = /* @__PURE__ */ new Set();
      for (let o3 = t2.length - 1; o3 >= 0; o3--) {
        const a2 = t2[o3];
        if (!r2.has(a2)) {
          const t3 = [];
          fa(a2, t3, r2, e3, s2, i2), n2.unshift(t3);
        }
      }
      const o2 = /* @__PURE__ */ new Set();
      for (const e4 of n2)
        for (const t3 of e4)
          o2.add(t3);
      return o2;
    }(this, this.orderedModules, this.chunkByModule, this.externalChunkByModule);
    for (const e3 of this.orderedModules)
      this.addImplicitlyLoadedBeforeFromModule(e3), this.setUpChunkImportsAndExportsForModule(e3);
  }
  async render() {
    const { dependencies: e3, exportMode: t2, facadeModule: s2, inputOptions: { onLog: i2 }, outputOptions: n2, pluginDriver: r2, snippets: o2 } = this, { format: a2, hoistTransitiveImports: l2, preserveModules: h2 } = n2;
    if (l2 && !h2 && null !== s2)
      for (const t3 of e3)
        t3 instanceof Pa && this.inlineChunkDependencies(t3);
    const c2 = this.getPreliminaryFileName(), { accessedGlobals: u2, indent: d2, magicString: p2, renderedSource: f2, usedModules: m2, usesTopLevelAwait: g2 } = this.renderModules(c2.fileName), y2 = [...this.getRenderedDependencies().values()], x2 = "none" === t2 ? [] : this.getChunkExportDeclarations(a2);
    let E2 = x2.length > 0, b2 = false;
    for (const e4 of y2) {
      const { reexports: t3 } = e4;
      (t3 == null ? void 0 : t3.length) && (E2 = true, !b2 && t3.some((e5) => "default" === e5.reexported) && (b2 = true), "es" === a2 && (e4.reexports = t3.filter(({ reexported: e5 }) => !x2.find(({ exported: t4 }) => t4 === e5))));
    }
    if (!b2) {
      for (const { exported: e4 } of x2)
        if ("default" === e4) {
          b2 = true;
          break;
        }
    }
    const { intro: v2, outro: S2, banner: A2, footer: k2 } = await aa(n2, r2, this.getRenderedChunkInfo());
    return na[a2](f2, { accessedGlobals: u2, dependencies: y2, exports: x2, hasDefaultExport: b2, hasExports: E2, id: c2.fileName, indent: d2, intro: v2, isEntryFacade: h2 || null !== s2 && s2.info.isEntry, isModuleFacade: null !== s2, log: i2, namedExportsMode: "default" !== t2, outro: S2, snippets: o2, usesTopLevelAwait: g2 }, n2), A2 && p2.prepend(A2), k2 && p2.append(k2), { chunk: this, magicString: p2, preliminaryFileName: c2, usedModules: m2 };
  }
  addImplicitlyLoadedBeforeFromModule(e3) {
    const { chunkByModule: t2, implicitlyLoadedBefore: s2 } = this;
    for (const i2 of e3.implicitlyLoadedBefore) {
      const e4 = t2.get(i2);
      e4 && e4 !== this && s2.add(e4);
    }
  }
  addNecessaryImportsForFacades() {
    for (const [e3, t2] of this.includedReexportsByModule)
      if (this.includedNamespaces.has(e3))
        for (const e4 of t2)
          this.imports.add(e4);
  }
  assignFacadeName({ fileName: e3, name: t2 }, s2, i2) {
    e3 ? this.fileName = e3 : this.name = this.outputOptions.sanitizeFileName(t2 || (i2 ? this.getPreserveModulesChunkNameFromModule(s2) : Ca(s2)));
  }
  checkCircularDependencyImport(e3, t2) {
    var _a2;
    const s2 = e3.module;
    if (s2 instanceof Oo) {
      const l2 = this.chunkByModule.get(s2);
      let h2;
      do {
        if (h2 = t2.alternativeReexportModules.get(e3), h2) {
          this.chunkByModule.get(h2) !== l2 && this.inputOptions.onLog(ve, (i2 = ((_a2 = s2.getExportNamesByVariable().get(e3)) == null ? void 0 : _a2[0]) || "*", n2 = s2.id, r2 = h2.id, o2 = t2.id, a2 = this.outputOptions.preserveModules, { code: "CYCLIC_CROSS_CHUNK_REEXPORT", exporter: n2, id: o2, message: `Export "${i2}" of module "${T(n2)}" was reexported through module "${T(r2)}" while both modules are dependencies of each other and will end up in different chunks by current Rollup settings. This scenario is not well supported at the moment as it will produce a circular dependency between chunks and will likely lead to broken execution order.
Either change the import in "${T(o2)}" to point directly to the exporting module or ${a2 ? 'do not use "output.preserveModules"' : 'reconfigure "output.manualChunks"'} to ensure these modules end up in the same chunk.`, reexporter: r2 })), t2 = h2;
        }
      } while (h2);
    }
    var i2, n2, r2, o2, a2;
  }
  ensureReexportsAreAvailableForModule(e3) {
    const t2 = [], s2 = e3.getExportNamesByVariable();
    for (const i2 of s2.keys()) {
      const s3 = i2 instanceof po, n2 = s3 ? i2.getBaseVariable() : i2;
      if (this.checkCircularDependencyImport(n2, e3), !(n2 instanceof uo && this.outputOptions.preserveModules)) {
        const e4 = n2.module;
        if (e4 instanceof Oo) {
          const i3 = this.chunkByModule.get(e4);
          i3 && i3 !== this && (i3.exports.add(n2), t2.push(n2), s3 && this.imports.add(n2));
        }
      }
    }
    t2.length > 0 && this.includedReexportsByModule.set(e3, t2);
  }
  generateVariableName() {
    if (this.manualChunkAlias)
      return this.manualChunkAlias;
    const e3 = this.entryModules[0] || this.implicitEntryModules[0] || this.dynamicEntryModules[0] || this.orderedModules[this.orderedModules.length - 1];
    return e3 ? Ca(e3) : "chunk";
  }
  getChunkExportDeclarations(e3) {
    const t2 = [];
    for (const s2 of this.getExportNames()) {
      if ("*" === s2[0])
        continue;
      const i2 = this.exportsByName.get(s2);
      if (!(i2 instanceof po)) {
        const t3 = i2.module;
        if (t3) {
          const i3 = this.chunkByModule.get(t3);
          if (i3 !== this) {
            if (!i3 || "es" !== e3)
              continue;
            const t4 = this.renderedDependencies.get(i3);
            if (!t4)
              continue;
            const { imports: n3, reexports: r3 } = t4, o3 = r3 == null ? void 0 : r3.find(({ reexported: e4 }) => e4 === s2), a2 = n3 == null ? void 0 : n3.find(({ imported: e4 }) => e4 === (o3 == null ? void 0 : o3.imported));
            if (!a2)
              continue;
          }
        }
      }
      let n2 = null, r2 = false, o2 = i2.getName(this.snippets.getPropertyAccess);
      if (i2 instanceof Ii) {
        for (const e4 of i2.declarations)
          if (e4.parent instanceof er || e4 instanceof tr && e4.declaration instanceof er) {
            r2 = true;
            break;
          }
      } else
        i2 instanceof po && (n2 = o2, "es" === e3 && (o2 = i2.renderName));
      t2.push({ exported: s2, expression: n2, hoisted: r2, local: o2 });
    }
    return t2;
  }
  getDependenciesToBeDeconflicted(e3, t2, s2) {
    const i2 = /* @__PURE__ */ new Set(), n2 = /* @__PURE__ */ new Set(), r2 = /* @__PURE__ */ new Set();
    for (const t3 of [...this.exportNamesByVariable.keys(), ...this.imports])
      if (e3 || t3.isNamespace) {
        const o2 = t3.module;
        if (o2 instanceof Qt) {
          const a2 = this.externalChunkByModule.get(o2);
          i2.add(a2), e3 && ("default" === t3.name ? gr[s2(o2.id)] && n2.add(a2) : "*" === t3.name && xr[s2(o2.id)] && r2.add(a2));
        } else {
          const s3 = this.chunkByModule.get(o2);
          s3 !== this && (i2.add(s3), e3 && "default" === s3.exportMode && t3.isNamespace && r2.add(s3));
        }
      }
    if (t2)
      for (const e4 of this.dependencies)
        i2.add(e4);
    return { deconflictedDefault: n2, deconflictedNamespace: r2, dependencies: i2 };
  }
  getDynamicDependencies() {
    return this.getIncludedDynamicImports().map((e3) => e3.facadeChunk || e3.chunk || e3.externalChunk || e3.resolution).filter((e3) => e3 !== this && (e3 instanceof Pa || e3 instanceof z));
  }
  getDynamicImportStringAndAssertions(e3, t2) {
    if (e3 instanceof Qt) {
      const s2 = this.externalChunkByModule.get(e3);
      return [`'${s2.getImportPath(t2)}'`, s2.getImportAssertions(this.snippets)];
    }
    return [e3 || "", "es" === this.outputOptions.format && this.outputOptions.externalImportAssertions || null];
  }
  getFallbackChunkName() {
    return this.manualChunkAlias ? this.manualChunkAlias : this.dynamicName ? this.dynamicName : this.fileName ? L(this.fileName) : L(this.orderedModules[this.orderedModules.length - 1].id);
  }
  getImportSpecifiers() {
    const { interop: e3 } = this.outputOptions, t2 = /* @__PURE__ */ new Map();
    for (const s2 of this.imports) {
      const i2 = s2.module;
      let n2, r2;
      if (i2 instanceof Qt) {
        if (n2 = this.externalChunkByModule.get(i2), r2 = s2.name, "default" !== r2 && "*" !== r2 && "defaultOnly" === e3(i2.id))
          return Ye(qt(i2.id, r2, false));
      } else
        n2 = this.chunkByModule.get(i2), r2 = n2.getVariableExportName(s2);
      F(t2, n2, U).push({ imported: r2, local: s2.getName(this.snippets.getPropertyAccess) });
    }
    return t2;
  }
  getIncludedDynamicImports() {
    if (this.includedDynamicImports)
      return this.includedDynamicImports;
    const e3 = [];
    for (const t2 of this.orderedModules)
      for (const { node: s2, resolution: i2 } of t2.dynamicImports)
        s2.included && e3.push(i2 instanceof Oo ? { chunk: this.chunkByModule.get(i2), externalChunk: null, facadeChunk: this.facadeChunkByModule.get(i2), node: s2, resolution: i2 } : i2 instanceof Qt ? { chunk: null, externalChunk: this.externalChunkByModule.get(i2), facadeChunk: null, node: s2, resolution: i2 } : { chunk: null, externalChunk: null, facadeChunk: null, node: s2, resolution: i2 });
    return this.includedDynamicImports = e3;
  }
  getPreRenderedChunkInfo() {
    if (this.preRenderedChunkInfo)
      return this.preRenderedChunkInfo;
    const { dynamicEntryModules: e3, facadeModule: t2, implicitEntryModules: s2, orderedModules: i2 } = this;
    return this.preRenderedChunkInfo = { exports: this.getExportNames(), facadeModuleId: t2 && t2.id, isDynamicEntry: e3.length > 0, isEntry: !!(t2 == null ? void 0 : t2.info.isEntry), isImplicitEntry: s2.length > 0, moduleIds: i2.map(({ id: e4 }) => e4), name: this.getChunkName(), type: "chunk" };
  }
  getPreserveModulesChunkNameFromModule(e3) {
    const t2 = $a(e3);
    if (t2)
      return t2;
    const { preserveModulesRoot: s2, sanitizeFileName: i2 } = this.outputOptions, n2 = i2(I(e3.id.split(_a, 1)[0])), r2 = C(n2), o2 = Ia.has(r2) ? n2.slice(0, -r2.length) : n2;
    return A(o2) ? s2 && N(o2).startsWith(s2) ? o2.slice(s2.length).replace(/^[/\\]/, "") : $(this.inputBase, o2) : `_virtual/${w(o2)}`;
  }
  getReexportSpecifiers() {
    const { externalLiveBindings: e3, interop: t2 } = this.outputOptions, s2 = /* @__PURE__ */ new Map();
    for (let i2 of this.getExportNames()) {
      let n2, r2, o2 = false;
      if ("*" === i2[0]) {
        const s3 = i2.slice(1);
        "defaultOnly" === t2(s3) && this.inputOptions.onLog(ve, Ht(s3)), o2 = e3, n2 = this.externalChunkByModule.get(this.modulesById.get(s3)), r2 = i2 = "*";
      } else {
        const s3 = this.exportsByName.get(i2);
        if (s3 instanceof po)
          continue;
        const a2 = s3.module;
        if (a2 instanceof Oo) {
          if (n2 = this.chunkByModule.get(a2), n2 === this)
            continue;
          r2 = n2.getVariableExportName(s3), o2 = s3.isReassigned;
        } else {
          if (n2 = this.externalChunkByModule.get(a2), r2 = s3.name, "default" !== r2 && "*" !== r2 && "defaultOnly" === t2(a2.id))
            return Ye(qt(a2.id, r2, true));
          o2 = e3 && ("default" !== r2 || yr(t2(a2.id), true));
        }
      }
      F(s2, n2, U).push({ imported: r2, needsLiveBinding: o2, reexported: i2 });
    }
    return s2;
  }
  getReferencedFiles() {
    const e3 = /* @__PURE__ */ new Set();
    for (const t2 of this.orderedModules)
      for (const s2 of t2.importMetas) {
        const t3 = s2.getReferencedFileName(this.pluginDriver);
        t3 && e3.add(t3);
      }
    return [...e3];
  }
  getRenderedDependencies() {
    if (this.renderedDependencies)
      return this.renderedDependencies;
    const e3 = this.getImportSpecifiers(), t2 = this.getReexportSpecifiers(), s2 = /* @__PURE__ */ new Map(), i2 = this.getFileName();
    for (const n2 of this.dependencies) {
      const r2 = e3.get(n2) || null, o2 = t2.get(n2) || null, a2 = n2 instanceof z || "default" !== n2.exportMode, l2 = n2.getImportPath(i2);
      s2.set(n2, { assertions: n2 instanceof z ? n2.getImportAssertions(this.snippets) : null, defaultVariableName: n2.defaultVariableName, globalName: n2 instanceof z && ("umd" === this.outputOptions.format || "iife" === this.outputOptions.format) && wa(n2, this.outputOptions.globals, null !== (r2 || o2), this.inputOptions.onLog), importPath: l2, imports: r2, isChunk: n2 instanceof Pa, name: n2.variableName, namedExportsMode: a2, namespaceVariableName: n2.namespaceVariableName, reexports: o2 });
    }
    return this.renderedDependencies = s2;
  }
  inlineChunkDependencies(e3) {
    for (const t2 of e3.dependencies)
      this.dependencies.has(t2) || (this.dependencies.add(t2), t2 instanceof Pa && this.inlineChunkDependencies(t2));
  }
  renderModules(e3) {
    const { accessedGlobalsByScope: t2, dependencies: s2, exportNamesByVariable: i2, includedNamespaces: n2, inputOptions: { onLog: r2 }, isEmpty: o2, orderedModules: a2, outputOptions: c2, pluginDriver: f2, renderedModules: m2, snippets: x2 } = this, { compact: E2, dynamicImportFunction: b2, format: v2, freeze: S2, namespaceToStringTag: A2 } = c2, { _: k2, cnst: I2, n: w2 } = x2;
    this.setDynamicImportResolutions(e3), this.setImportMetaResolutions(e3), this.setIdentifierRenderResolutions();
    const P2 = new class e4 {
      constructor(e5 = {}) {
        this.intro = e5.intro || "", this.separator = void 0 !== e5.separator ? e5.separator : "\n", this.sources = [], this.uniqueSources = [], this.uniqueSourceIndexByFilename = {};
      }
      addSource(e5) {
        if (e5 instanceof g)
          return this.addSource({ content: e5, filename: e5.filename, separator: this.separator });
        if (!u(e5) || !e5.content)
          throw new Error("bundle.addSource() takes an object with a `content` property, which should be an instance of MagicString, and an optional `filename`");
        if (["filename", "ignoreList", "indentExclusionRanges", "separator"].forEach((t3) => {
          y.call(e5, t3) || (e5[t3] = e5.content[t3]);
        }), void 0 === e5.separator && (e5.separator = this.separator), e5.filename)
          if (y.call(this.uniqueSourceIndexByFilename, e5.filename)) {
            const t3 = this.uniqueSources[this.uniqueSourceIndexByFilename[e5.filename]];
            if (e5.content.original !== t3.content)
              throw new Error(`Illegal source: same filename (${e5.filename}), different contents`);
          } else
            this.uniqueSourceIndexByFilename[e5.filename] = this.uniqueSources.length, this.uniqueSources.push({ filename: e5.filename, content: e5.content.original });
        return this.sources.push(e5), this;
      }
      append(e5, t3) {
        return this.addSource({ content: new g(e5), separator: t3 && t3.separator || "" }), this;
      }
      clone() {
        const t3 = new e4({ intro: this.intro, separator: this.separator });
        return this.sources.forEach((e5) => {
          t3.addSource({ filename: e5.filename, content: e5.content.clone(), separator: e5.separator });
        }), t3;
      }
      generateDecodedMap(e5 = {}) {
        const t3 = [];
        let s3;
        this.sources.forEach((e6) => {
          Object.keys(e6.content.storedNames).forEach((e7) => {
            ~t3.indexOf(e7) || t3.push(e7);
          });
        });
        const i3 = new p(e5.hires);
        return this.intro && i3.advance(this.intro), this.sources.forEach((e6, n3) => {
          n3 > 0 && i3.advance(this.separator);
          const r3 = e6.filename ? this.uniqueSourceIndexByFilename[e6.filename] : -1, o3 = e6.content, a3 = d(o3.original);
          o3.intro && i3.advance(o3.intro), o3.firstChunk.eachNext((s4) => {
            const n4 = a3(s4.start);
            s4.intro.length && i3.advance(s4.intro), e6.filename ? s4.edited ? i3.addEdit(r3, s4.content, n4, s4.storeName ? t3.indexOf(s4.original) : -1) : i3.addUneditedChunk(r3, s4, o3.original, n4, o3.sourcemapLocations) : i3.advance(s4.content), s4.outro.length && i3.advance(s4.outro);
          }), o3.outro && i3.advance(o3.outro), e6.ignoreList && -1 !== r3 && (void 0 === s3 && (s3 = []), s3.push(r3));
        }), { file: e5.file ? e5.file.split(/[/\\]/).pop() : void 0, sources: this.uniqueSources.map((t4) => e5.file ? h(e5.file, t4.filename) : t4.filename), sourcesContent: this.uniqueSources.map((t4) => e5.includeContent ? t4.content : null), names: t3, mappings: i3.raw, x_google_ignoreList: s3 };
      }
      generateMap(e5) {
        return new l(this.generateDecodedMap(e5));
      }
      getIndentString() {
        const e5 = {};
        return this.sources.forEach((t3) => {
          const s3 = t3.content._getRawIndentString();
          null !== s3 && (e5[s3] || (e5[s3] = 0), e5[s3] += 1);
        }), Object.keys(e5).sort((t3, s3) => e5[t3] - e5[s3])[0] || "	";
      }
      indent(e5) {
        if (arguments.length || (e5 = this.getIndentString()), "" === e5)
          return this;
        let t3 = !this.intro || "\n" === this.intro.slice(-1);
        return this.sources.forEach((s3, i3) => {
          const n3 = void 0 !== s3.separator ? s3.separator : this.separator, r3 = t3 || i3 > 0 && /\r?\n$/.test(n3);
          s3.content.indent(e5, { exclude: s3.indentExclusionRanges, indentStart: r3 }), t3 = "\n" === s3.content.lastChar();
        }), this.intro && (this.intro = e5 + this.intro.replace(/^[^\n]/gm, (t4, s3) => s3 > 0 ? e5 + t4 : t4)), this;
      }
      prepend(e5) {
        return this.intro = e5 + this.intro, this;
      }
      toString() {
        const e5 = this.sources.map((e6, t3) => {
          const s3 = void 0 !== e6.separator ? e6.separator : this.separator;
          return (t3 > 0 ? s3 : "") + e6.content.toString();
        }).join("");
        return this.intro + e5;
      }
      isEmpty() {
        return !(this.intro.length && this.intro.trim() || this.sources.some((e5) => !e5.content.isEmpty()));
      }
      length() {
        return this.sources.reduce((e5, t3) => e5 + t3.content.length(), this.intro.length);
      }
      trimLines() {
        return this.trim("[\\r\\n]");
      }
      trim(e5) {
        return this.trimStart(e5).trimEnd(e5);
      }
      trimStart(e5) {
        const t3 = new RegExp("^" + (e5 || "\\s") + "+");
        if (this.intro = this.intro.replace(t3, ""), !this.intro) {
          let t4, s3 = 0;
          do {
            if (t4 = this.sources[s3++], !t4)
              break;
          } while (!t4.content.trimStartAborted(e5));
        }
        return this;
      }
      trimEnd(e5) {
        const t3 = new RegExp((e5 || "\\s") + "+$");
        let s3, i3 = this.sources.length - 1;
        do {
          if (s3 = this.sources[i3--], !s3) {
            this.intro = this.intro.replace(t3, "");
            break;
          }
        } while (!s3.content.trimEndAborted(e5));
        return this;
      }
    }({ separator: `${w2}${w2}` }), C2 = function(e4, t3) {
      if (true !== t3.indent)
        return t3.indent;
      for (const t4 of e4) {
        const e5 = pa(t4.originalCode);
        if (null !== e5)
          return e5;
      }
      return "	";
    }(a2, c2), $2 = [];
    let N2 = "";
    const _2 = /* @__PURE__ */ new Set(), R2 = /* @__PURE__ */ new Map(), O2 = { dynamicImportFunction: b2, exportNamesByVariable: i2, format: v2, freeze: S2, indent: C2, namespaceToStringTag: A2, pluginDriver: f2, snippets: x2, useOriginalName: null };
    let D2 = false;
    for (const e4 of a2) {
      let s3, i3 = 0;
      if (e4.isIncluded() || n2.has(e4)) {
        const r4 = e4.render(O2);
        ({ source: s3 } = r4), D2 || (D2 = r4.usesTopLevelAwait), i3 = s3.length(), i3 && (E2 && s3.lastLine().includes("//") && s3.append("\n"), R2.set(e4, s3), P2.addSource(s3), $2.push(e4));
        const o4 = e4.namespace;
        if (n2.has(e4)) {
          const e5 = o4.renderBlock(O2);
          o4.renderFirst() ? N2 += w2 + e5 : P2.addSource(new g(e5));
        }
        const a3 = t2.get(e4.scope);
        if (a3)
          for (const e5 of a3)
            _2.add(e5);
      }
      const { renderedExports: r3, removedExports: o3 } = e4.getRenderedExports();
      m2[e4.id] = { get code() {
        return (s3 == null ? void 0 : s3.toString()) ?? null;
      }, originalLength: e4.originalCode.length, removedExports: o3, renderedExports: r3, renderedLength: i3 };
    }
    N2 && P2.prepend(N2 + w2 + w2), this.needsExportsShim && P2.prepend(`${w2}${I2} ${ho}${k2}=${k2}void 0;${w2}${w2}`);
    const L2 = E2 ? P2 : P2.trim();
    var T2;
    return o2 && 0 === this.getExportNames().length && 0 === s2.size && r2(ve, { code: "EMPTY_BUNDLE", message: `Generated an empty chunk: "${T2 = this.getChunkName()}".`, names: [T2] }), { accessedGlobals: _2, indent: C2, magicString: P2, renderedSource: L2, usedModules: $2, usesTopLevelAwait: D2 };
  }
  setDynamicImportResolutions(e3) {
    const { accessedGlobalsByScope: t2, outputOptions: s2, pluginDriver: i2, snippets: n2 } = this;
    for (const r2 of this.getIncludedDynamicImports())
      if (r2.chunk) {
        const { chunk: o2, facadeChunk: a2, node: l2, resolution: h2 } = r2;
        o2 === this ? l2.setInternalResolution(h2.namespace) : l2.setExternalResolution((a2 || o2).exportMode, h2, s2, n2, i2, t2, `'${(a2 || o2).getImportPath(e3)}'`, !(a2 == null ? void 0 : a2.strictFacade) && o2.exportNamesByVariable.get(h2.namespace)[0], null);
      } else {
        const { node: o2, resolution: a2 } = r2, [l2, h2] = this.getDynamicImportStringAndAssertions(a2, e3);
        o2.setExternalResolution("external", a2, s2, n2, i2, t2, l2, false, h2);
      }
  }
  setIdentifierRenderResolutions() {
    const { format: e3, interop: t2, namespaceToStringTag: s2, preserveModules: i2, externalLiveBindings: n2 } = this.outputOptions, r2 = /* @__PURE__ */ new Set();
    for (const t3 of this.getExportNames()) {
      const s3 = this.exportsByName.get(t3);
      "es" !== e3 && "system" !== e3 && s3.isReassigned && !s3.isId ? s3.setRenderNames("exports", t3) : s3 instanceof po ? r2.add(s3) : s3.setRenderNames(null, null);
    }
    for (const e4 of this.orderedModules)
      if (e4.needsExportShim) {
        this.needsExportsShim = true;
        break;
      }
    const o2 = /* @__PURE__ */ new Set(["Object", "Promise"]);
    switch (this.needsExportsShim && o2.add(ho), s2 && o2.add("Symbol"), e3) {
      case "system":
        o2.add("module").add("exports");
        break;
      case "es":
        break;
      case "cjs":
        o2.add("module").add("require").add("__filename").add("__dirname");
      default:
        o2.add("exports");
        for (const e4 of Dr)
          o2.add(e4);
    }
    ha(this.orderedModules, this.getDependenciesToBeDeconflicted("es" !== e3 && "system" !== e3, "amd" === e3 || "umd" === e3 || "iife" === e3, t2), this.imports, o2, e3, t2, i2, n2, this.chunkByModule, this.externalChunkByModule, r2, this.exportNamesByVariable, this.accessedGlobalsByScope, this.includedNamespaces);
  }
  setImportMetaResolutions(e3) {
    const { accessedGlobalsByScope: t2, includedNamespaces: s2, orderedModules: i2, outputOptions: { format: n2 } } = this;
    for (const r2 of i2) {
      for (const s3 of r2.importMetas)
        s3.setResolution(n2, t2, e3);
      s2.has(r2) && r2.namespace.prepare(t2);
    }
  }
  setUpChunkImportsAndExportsForModule(e3) {
    const t2 = new Set(e3.includedImports);
    if (!this.outputOptions.preserveModules && this.includedNamespaces.has(e3)) {
      const s2 = e3.namespace.getMemberVariables();
      for (const e4 of Object.values(s2))
        e4.included && t2.add(e4);
    }
    for (let s2 of t2) {
      s2 instanceof no && (s2 = s2.getOriginalVariable()), s2 instanceof po && (s2 = s2.getBaseVariable());
      const t3 = this.chunkByModule.get(s2.module);
      t3 !== this && (this.imports.add(s2), s2.module instanceof Oo && (this.checkCircularDependencyImport(s2, e3), s2 instanceof uo && this.outputOptions.preserveModules || t3.exports.add(s2)));
    }
    (this.includedNamespaces.has(e3) || e3.info.isEntry && false !== e3.preserveSignature || e3.includedDynamicImporters.some((e4) => this.chunkByModule.get(e4) !== this)) && this.ensureReexportsAreAvailableForModule(e3);
    for (const { node: t3, resolution: s2 } of e3.dynamicImports)
      t3.included && s2 instanceof Oo && this.chunkByModule.get(s2) === this && !this.includedNamespaces.has(s2) && (this.includedNamespaces.add(s2), this.ensureReexportsAreAvailableForModule(s2));
  }
}
function Ca(e3) {
  return $a(e3) ?? L(e3.id);
}
function $a(e3) {
  var _a2, _b;
  return ((_a2 = e3.chunkNames.find(({ isUserDefined: e4 }) => e4)) == null ? void 0 : _a2.name) ?? ((_b = e3.chunkNames[0]) == null ? void 0 : _b.name);
}
function Na(e3, t2) {
  const s2 = {};
  for (const [i2, n2] of e3) {
    const e4 = /* @__PURE__ */ new Set();
    if (n2.imports)
      for (const { imported: t3 } of n2.imports)
        e4.add(t3);
    if (n2.reexports)
      for (const { imported: t3 } of n2.reexports)
        e4.add(t3);
    s2[t2(i2)] = [...e4];
  }
  return s2;
}
const _a = /[#?]/, Ra = (e3) => e3.getFileName();
function* Oa(e3) {
  for (const t2 of e3)
    yield* t2;
}
function Da(e3, t2, s2, i2) {
  const { chunkDefinitions: n2, modulesInManualChunks: r2 } = function(e4) {
    const t3 = [], s3 = new Set(e4.keys()), i3 = /* @__PURE__ */ Object.create(null);
    for (const [t4, n3] of e4)
      La(t4, i3[n3] || (i3[n3] = []), s3);
    for (const [e5, s4] of Object.entries(i3))
      t3.push({ alias: e5, modules: s4 });
    return { chunkDefinitions: t3, modulesInManualChunks: s3 };
  }(t2), { allEntries: o2, dependentEntriesByModule: a2, dynamicallyDependentEntriesByDynamicEntry: l2, dynamicImportsByEntry: h2 } = function(e4) {
    const t3 = /* @__PURE__ */ new Set(), s3 = /* @__PURE__ */ new Map(), i3 = [], n3 = new Set(e4);
    let r3 = 0;
    for (const e5 of n3) {
      const o4 = /* @__PURE__ */ new Set();
      i3.push(o4);
      const a4 = /* @__PURE__ */ new Set([e5]);
      for (const e6 of a4) {
        F(s3, e6, j).add(r3);
        for (const t4 of e6.getDependenciesToBeIncluded())
          t4 instanceof Qt || a4.add(t4);
        for (const { resolution: s4 } of e6.dynamicImports)
          s4 instanceof Oo && s4.includedDynamicImporters.length > 0 && !n3.has(s4) && (t3.add(s4), n3.add(s4), o4.add(s4));
        for (const s4 of e6.implicitlyLoadedBefore)
          n3.has(s4) || (t3.add(s4), n3.add(s4));
      }
      r3++;
    }
    const o3 = [...n3], { dynamicEntries: a3, dynamicImportsByEntry: l3 } = function(e5, t4, s4) {
      const i4 = /* @__PURE__ */ new Map(), n4 = /* @__PURE__ */ new Set();
      for (const [s5, r5] of e5.entries())
        i4.set(r5, s5), t4.has(r5) && n4.add(s5);
      const r4 = [];
      for (const e6 of s4) {
        const t5 = /* @__PURE__ */ new Set();
        for (const s5 of e6)
          t5.add(i4.get(s5));
        r4.push(t5);
      }
      return { dynamicEntries: n4, dynamicImportsByEntry: r4 };
    }(o3, t3, i3);
    return { allEntries: o3, dependentEntriesByModule: s3, dynamicallyDependentEntriesByDynamicEntry: Ta(s3, a3, o3), dynamicImportsByEntry: l3 };
  }(e3), c2 = Ma(function* (e4, t3) {
    for (const [s3, i3] of e4)
      t3.has(s3) || (yield { dependentEntries: i3, modules: [s3] });
  }(a2, r2));
  return function(e4, t3, s3, i3) {
    const n3 = i3.map(() => 0n), r3 = i3.map((e5, s4) => t3.has(s4) ? -1n : 0n);
    let o3 = 1n;
    for (const { dependentEntries: t4 } of e4) {
      for (const e5 of t4)
        n3[e5] |= o3;
      o3 <<= 1n;
    }
    const a3 = t3;
    for (const [e5, t4] of a3) {
      a3.delete(e5);
      const i4 = r3[e5];
      let o4 = i4;
      for (const e6 of t4)
        o4 &= n3[e6] | r3[e6];
      if (o4 !== i4) {
        r3[e5] = o4;
        for (const t5 of s3[e5])
          F(a3, t5, j).add(e5);
      }
    }
    o3 = 1n;
    for (const { dependentEntries: t4 } of e4) {
      for (const e5 of t4)
        (r3[e5] & o3) === o3 && t4.delete(e5);
      o3 <<= 1n;
    }
  }(c2, l2, h2, o2), n2.push(...function(e4, t3, s3, i3) {
    Io("optimize chunks", 3);
    const n3 = function(e5, t4, s4) {
      const i4 = [], n4 = [], r3 = /* @__PURE__ */ new Map(), o3 = [];
      let a3 = 0n, l3 = 1n;
      for (const { dependentEntries: t5, modules: h3 } of e5) {
        const e6 = { containedAtoms: l3, correlatedAtoms: 0n, dependencies: /* @__PURE__ */ new Set(), dependentChunks: /* @__PURE__ */ new Set(), dependentEntries: t5, modules: h3, pure: true, size: 0 };
        let c3 = 0, u2 = true;
        for (const t6 of h3)
          r3.set(t6, e6), t6.isIncluded() && (u2 && (u2 = !t6.hasEffects()), c3 += s4 > 1 ? t6.estimateSize() : 1);
        e6.pure = u2, e6.size = c3, o3.push(c3), u2 || (a3 |= l3), (c3 < s4 ? i4 : n4).push(e6), l3 <<= 1n;
      }
      if (0 === i4.length)
        return null;
      return a3 |= function(e6, t5, s5, i5) {
        const n5 = /* @__PURE__ */ new Map();
        let r4 = 0n;
        const o4 = [];
        for (let e7 = 0; e7 < s5; e7++)
          o4.push(0n);
        for (const s6 of e6) {
          s6.sort(za);
          for (const e7 of s6) {
            const { dependencies: s7, dependentEntries: a4, modules: l4 } = e7;
            for (const o5 of l4)
              for (const a5 of o5.getDependenciesToBeIncluded())
                if (a5 instanceof Qt)
                  a5.info.moduleSideEffects && (e7.containedAtoms |= F(n5, a5, () => {
                    const e8 = i5;
                    return i5 <<= 1n, r4 |= e8, e8;
                  }));
                else {
                  const i6 = t5.get(a5);
                  i6 && i6 !== e7 && (s7.add(i6), i6.dependentChunks.add(e7));
                }
            const { containedAtoms: h3 } = e7;
            for (const e8 of a4)
              o4[e8] |= h3;
          }
        }
        for (const t6 of e6)
          for (const e7 of t6) {
            const { dependentEntries: t7 } = e7;
            e7.correlatedAtoms = -1n;
            for (const s6 of t7)
              e7.correlatedAtoms &= o4[s6];
          }
        return r4;
      }([n4, i4], r3, t4, l3), { big: new Set(n4), sideEffectAtoms: a3, sizeByAtom: o3, small: new Set(i4) };
    }(e4, t3, s3);
    if (!n3)
      return wo("optimize chunks", 3), e4;
    return s3 > 1 && i3("info", Ut(e4.length, n3.small.size, "Initially")), function(e5, t4) {
      const { small: s4 } = e5;
      for (const i4 of s4) {
        const n4 = Va(i4, e5, t4 <= 1 ? 1 : 1 / 0);
        if (n4) {
          const { containedAtoms: r3, correlatedAtoms: o3, modules: a3, pure: l3, size: h3 } = i4;
          s4.delete(i4), Ba(n4, t4, e5).delete(n4), n4.modules.push(...a3), n4.size += h3, n4.pure && (n4.pure = l3);
          const { dependencies: c3, dependentChunks: u2, dependentEntries: d2 } = n4;
          n4.correlatedAtoms &= o3, n4.containedAtoms |= r3;
          for (const e6 of i4.dependentEntries)
            d2.add(e6);
          for (const e6 of i4.dependencies)
            c3.add(e6), e6.dependentChunks.delete(i4), e6.dependentChunks.add(n4);
          for (const e6 of i4.dependentChunks)
            u2.add(e6), e6.dependencies.delete(i4), e6.dependencies.add(n4);
          c3.delete(n4), u2.delete(n4), Ba(n4, t4, e5).add(n4);
        }
      }
    }(n3, s3), s3 > 1 && i3("info", Ut(n3.small.size + n3.big.size, n3.small.size, "After merging chunks")), wo("optimize chunks", 3), [...n3.small, ...n3.big];
  }(Ma(c2), o2.length, s2, i2).map(({ modules: e4 }) => ({ alias: null, modules: e4 }))), n2;
}
function La(e3, t2, s2) {
  const i2 = /* @__PURE__ */ new Set([e3]);
  for (const e4 of i2) {
    s2.add(e4), t2.push(e4);
    for (const t3 of e4.dependencies)
      t3 instanceof Qt || s2.has(t3) || i2.add(t3);
  }
}
function Ta(e3, t2, s2) {
  const i2 = /* @__PURE__ */ new Map();
  for (const n2 of t2) {
    const t3 = F(i2, n2, j), r2 = s2[n2];
    for (const s3 of Oa([r2.includedDynamicImporters, r2.implicitlyLoadedAfter]))
      for (const i3 of e3.get(s3))
        t3.add(i3);
  }
  return i2;
}
function Ma(e3) {
  var t2;
  const s2 = /* @__PURE__ */ Object.create(null);
  for (const { dependentEntries: i2, modules: n2 } of e3) {
    let e4 = 0n;
    for (const t3 of i2)
      e4 |= 1n << BigInt(t3);
    (s2[t2 = String(e4)] || (s2[t2] = { dependentEntries: new Set(i2), modules: [] })).modules.push(...n2);
  }
  return Object.values(s2);
}
function Va(e3, { big: t2, sideEffectAtoms: s2, sizeByAtom: i2, small: n2 }, r2) {
  let o2 = null;
  for (const a2 of Oa([n2, t2])) {
    if (e3 === a2)
      continue;
    const t3 = Fa(e3, a2, r2, s2, i2);
    if (t3 < r2) {
      if (o2 = a2, 0 === t3)
        break;
      r2 = t3;
    }
  }
  return o2;
}
function Ba(e3, t2, s2) {
  return e3.size < t2 ? s2.small : s2.big;
}
function za({ size: e3 }, { size: t2 }) {
  return e3 - t2;
}
function Fa(e3, t2, s2, i2, n2) {
  const r2 = ja(e3, t2, s2, i2, n2);
  return r2 < s2 ? r2 + ja(t2, e3, s2 - r2, i2, n2) : 1 / 0;
}
function ja(e3, t2, s2, i2, n2) {
  const { correlatedAtoms: r2 } = t2;
  let o2 = e3.containedAtoms;
  const a2 = o2 & i2;
  if ((r2 & a2) !== a2)
    return 1 / 0;
  const l2 = new Set(e3.dependencies);
  for (const { dependencies: e4, containedAtoms: s3 } of l2) {
    o2 |= s3;
    const n3 = s3 & i2;
    if ((r2 & n3) !== n3)
      return 1 / 0;
    for (const s4 of e4) {
      if (s4 === t2)
        return 1 / 0;
      l2.add(s4);
    }
  }
  return function(e4, t3, s3) {
    let i3 = 0, n3 = 0, r3 = 1n;
    const { length: o3 } = s3;
    for (; n3 < o3; n3++)
      if ((e4 & r3) === r3 && (i3 += s3[n3]), r3 <<= 1n, i3 >= t3)
        return 1 / 0;
    return i3;
  }(o2 & ~r2, s2, n2);
}
const Ua = (e3, t2) => e3.execIndex > t2.execIndex ? 1 : -1;
function Ga(e3, t2, s2) {
  const i2 = Symbol(e3.id), n2 = [e3.id];
  let r2 = t2;
  for (e3.cycles.add(i2); r2 !== e3; )
    r2.cycles.add(i2), n2.push(r2.id), r2 = s2.get(r2);
  return n2.push(n2[0]), n2.reverse(), n2;
}
const Wa = (e3, t2) => t2 ? `(${e3})` : e3, qa = /^(?!\d)[\w$]+$/;
class Ha {
  constructor(e3, t2) {
    this.isOriginal = true, this.filename = e3, this.content = t2;
  }
  traceSegment(e3, t2, s2) {
    return { column: t2, line: e3, name: s2, source: this };
  }
}
class Ka {
  constructor(e3, t2) {
    this.sources = t2, this.names = e3.names, this.mappings = e3.mappings;
  }
  traceMappings() {
    const e3 = [], t2 = /* @__PURE__ */ new Map(), s2 = [], i2 = [], n2 = /* @__PURE__ */ new Map(), r2 = [];
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
          const { column: o4, line: h2, name: c2, source: { content: u2, filename: d2 } } = l2;
          let p2 = t2.get(d2);
          if (void 0 === p2)
            p2 = e3.length, e3.push(d2), t2.set(d2, p2), s2[p2] = u2;
          else if (null == s2[p2])
            s2[p2] = u2;
          else if (null != u2 && s2[p2] !== u2)
            return Ye(Wt(d2));
          const f2 = [r3[0], p2, h2, o4];
          if (c2) {
            let e4 = n2.get(c2);
            void 0 === e4 && (e4 = i2.length, i2.push(c2), n2.set(c2, e4)), f2[4] = e4;
          }
          a2.push(f2);
        }
      }
      r2.push(a2);
    }
    return { mappings: r2, names: i2, sources: e3, sourcesContent: s2 };
  }
  traceSegment(e3, t2, s2) {
    const i2 = this.mappings[e3];
    if (!i2)
      return null;
    let n2 = 0, r2 = i2.length - 1;
    for (; n2 <= r2; ) {
      const e4 = n2 + r2 >> 1, o2 = i2[e4];
      if (o2[0] === t2 || n2 === r2) {
        if (1 == o2.length)
          return null;
        const e5 = this.sources[o2[1]];
        return e5 ? e5.traceSegment(o2[2], o2[3], 5 === o2.length ? this.names[o2[4]] : s2) : null;
      }
      o2[0] > t2 ? r2 = e4 - 1 : n2 = e4 + 1;
    }
    return null;
  }
}
function Ya(e3) {
  return function(t2, s2) {
    return s2.mappings ? new Ka(s2, [t2]) : (e3(ve, (i2 = s2.plugin, { code: It, message: `Sourcemap is likely to be incorrect: a plugin (${i2}) was used to transform files, but didn't generate a sourcemap for the transformation. Consult the plugin documentation for help`, plugin: i2, url: Oe(Le) })), new Ka({ mappings: [], names: [] }, [t2]));
    var i2;
  };
}
function Xa(e3, t2, s2, i2, n2) {
  let r2;
  if (s2) {
    const t3 = s2.sources, i3 = s2.sourcesContent || [], n3 = P(e3) || ".", o2 = s2.sourceRoot || ".", a2 = t3.map((e4, t4) => new Ha(N(n3, o2, e4), i3[t4]));
    r2 = new Ka(s2, a2);
  } else
    r2 = new Ha(e3, t2);
  return i2.reduce(n2, r2);
}
var Qa = {}, Za = Ja;
function Ja(e3, t2) {
  if (!e3)
    throw new Error(t2 || "Assertion failed");
}
Ja.equal = function(e3, t2, s2) {
  if (e3 != t2)
    throw new Error(s2 || "Assertion failed: " + e3 + " != " + t2);
};
var el = { exports: {} };
"function" == typeof Object.create ? el.exports = function(e3, t2) {
  t2 && (e3.super_ = t2, e3.prototype = Object.create(t2.prototype, { constructor: { value: e3, enumerable: false, writable: true, configurable: true } }));
} : el.exports = function(e3, t2) {
  if (t2) {
    e3.super_ = t2;
    var s2 = function() {
    };
    s2.prototype = t2.prototype, e3.prototype = new s2(), e3.prototype.constructor = e3;
  }
};
var tl = el.exports, sl = Za, il = tl;
function nl(e3, t2) {
  return 55296 == (64512 & e3.charCodeAt(t2)) && (!(t2 < 0 || t2 + 1 >= e3.length) && 56320 == (64512 & e3.charCodeAt(t2 + 1)));
}
function rl(e3) {
  return (e3 >>> 24 | e3 >>> 8 & 65280 | e3 << 8 & 16711680 | (255 & e3) << 24) >>> 0;
}
function ol(e3) {
  return 1 === e3.length ? "0" + e3 : e3;
}
function al(e3) {
  return 7 === e3.length ? "0" + e3 : 6 === e3.length ? "00" + e3 : 5 === e3.length ? "000" + e3 : 4 === e3.length ? "0000" + e3 : 3 === e3.length ? "00000" + e3 : 2 === e3.length ? "000000" + e3 : 1 === e3.length ? "0000000" + e3 : e3;
}
Qa.inherits = il, Qa.toArray = function(e3, t2) {
  if (Array.isArray(e3))
    return e3.slice();
  if (!e3)
    return [];
  var s2 = [];
  if ("string" == typeof e3)
    if (t2) {
      if ("hex" === t2)
        for ((e3 = e3.replace(/[^a-z0-9]+/gi, "")).length % 2 != 0 && (e3 = "0" + e3), n2 = 0; n2 < e3.length; n2 += 2)
          s2.push(parseInt(e3[n2] + e3[n2 + 1], 16));
    } else
      for (var i2 = 0, n2 = 0; n2 < e3.length; n2++) {
        var r2 = e3.charCodeAt(n2);
        r2 < 128 ? s2[i2++] = r2 : r2 < 2048 ? (s2[i2++] = r2 >> 6 | 192, s2[i2++] = 63 & r2 | 128) : nl(e3, n2) ? (r2 = 65536 + ((1023 & r2) << 10) + (1023 & e3.charCodeAt(++n2)), s2[i2++] = r2 >> 18 | 240, s2[i2++] = r2 >> 12 & 63 | 128, s2[i2++] = r2 >> 6 & 63 | 128, s2[i2++] = 63 & r2 | 128) : (s2[i2++] = r2 >> 12 | 224, s2[i2++] = r2 >> 6 & 63 | 128, s2[i2++] = 63 & r2 | 128);
      }
  else
    for (n2 = 0; n2 < e3.length; n2++)
      s2[n2] = 0 | e3[n2];
  return s2;
}, Qa.toHex = function(e3) {
  for (var t2 = "", s2 = 0; s2 < e3.length; s2++)
    t2 += ol(e3[s2].toString(16));
  return t2;
}, Qa.htonl = rl, Qa.toHex32 = function(e3, t2) {
  for (var s2 = "", i2 = 0; i2 < e3.length; i2++) {
    var n2 = e3[i2];
    "little" === t2 && (n2 = rl(n2)), s2 += al(n2.toString(16));
  }
  return s2;
}, Qa.zero2 = ol, Qa.zero8 = al, Qa.join32 = function(e3, t2, s2, i2) {
  var n2 = s2 - t2;
  sl(n2 % 4 == 0);
  for (var r2 = new Array(n2 / 4), o2 = 0, a2 = t2; o2 < r2.length; o2++, a2 += 4) {
    var l2;
    l2 = "big" === i2 ? e3[a2] << 24 | e3[a2 + 1] << 16 | e3[a2 + 2] << 8 | e3[a2 + 3] : e3[a2 + 3] << 24 | e3[a2 + 2] << 16 | e3[a2 + 1] << 8 | e3[a2], r2[o2] = l2 >>> 0;
  }
  return r2;
}, Qa.split32 = function(e3, t2) {
  for (var s2 = new Array(4 * e3.length), i2 = 0, n2 = 0; i2 < e3.length; i2++, n2 += 4) {
    var r2 = e3[i2];
    "big" === t2 ? (s2[n2] = r2 >>> 24, s2[n2 + 1] = r2 >>> 16 & 255, s2[n2 + 2] = r2 >>> 8 & 255, s2[n2 + 3] = 255 & r2) : (s2[n2 + 3] = r2 >>> 24, s2[n2 + 2] = r2 >>> 16 & 255, s2[n2 + 1] = r2 >>> 8 & 255, s2[n2] = 255 & r2);
  }
  return s2;
}, Qa.rotr32 = function(e3, t2) {
  return e3 >>> t2 | e3 << 32 - t2;
}, Qa.rotl32 = function(e3, t2) {
  return e3 << t2 | e3 >>> 32 - t2;
}, Qa.sum32 = function(e3, t2) {
  return e3 + t2 >>> 0;
}, Qa.sum32_3 = function(e3, t2, s2) {
  return e3 + t2 + s2 >>> 0;
}, Qa.sum32_4 = function(e3, t2, s2, i2) {
  return e3 + t2 + s2 + i2 >>> 0;
}, Qa.sum32_5 = function(e3, t2, s2, i2, n2) {
  return e3 + t2 + s2 + i2 + n2 >>> 0;
}, Qa.sum64 = function(e3, t2, s2, i2) {
  var n2 = e3[t2], r2 = i2 + e3[t2 + 1] >>> 0, o2 = (r2 < i2 ? 1 : 0) + s2 + n2;
  e3[t2] = o2 >>> 0, e3[t2 + 1] = r2;
}, Qa.sum64_hi = function(e3, t2, s2, i2) {
  return (t2 + i2 >>> 0 < t2 ? 1 : 0) + e3 + s2 >>> 0;
}, Qa.sum64_lo = function(e3, t2, s2, i2) {
  return t2 + i2 >>> 0;
}, Qa.sum64_4_hi = function(e3, t2, s2, i2, n2, r2, o2, a2) {
  var l2 = 0, h2 = t2;
  return l2 += (h2 = h2 + i2 >>> 0) < t2 ? 1 : 0, l2 += (h2 = h2 + r2 >>> 0) < r2 ? 1 : 0, e3 + s2 + n2 + o2 + (l2 += (h2 = h2 + a2 >>> 0) < a2 ? 1 : 0) >>> 0;
}, Qa.sum64_4_lo = function(e3, t2, s2, i2, n2, r2, o2, a2) {
  return t2 + i2 + r2 + a2 >>> 0;
}, Qa.sum64_5_hi = function(e3, t2, s2, i2, n2, r2, o2, a2, l2, h2) {
  var c2 = 0, u2 = t2;
  return c2 += (u2 = u2 + i2 >>> 0) < t2 ? 1 : 0, c2 += (u2 = u2 + r2 >>> 0) < r2 ? 1 : 0, c2 += (u2 = u2 + a2 >>> 0) < a2 ? 1 : 0, e3 + s2 + n2 + o2 + l2 + (c2 += (u2 = u2 + h2 >>> 0) < h2 ? 1 : 0) >>> 0;
}, Qa.sum64_5_lo = function(e3, t2, s2, i2, n2, r2, o2, a2, l2, h2) {
  return t2 + i2 + r2 + a2 + h2 >>> 0;
}, Qa.rotr64_hi = function(e3, t2, s2) {
  return (t2 << 32 - s2 | e3 >>> s2) >>> 0;
}, Qa.rotr64_lo = function(e3, t2, s2) {
  return (e3 << 32 - s2 | t2 >>> s2) >>> 0;
}, Qa.shr64_hi = function(e3, t2, s2) {
  return e3 >>> s2;
}, Qa.shr64_lo = function(e3, t2, s2) {
  return (e3 << 32 - s2 | t2 >>> s2) >>> 0;
};
var ll = {}, hl = Qa, cl = Za;
function ul() {
  this.pending = null, this.pendingTotal = 0, this.blockSize = this.constructor.blockSize, this.outSize = this.constructor.outSize, this.hmacStrength = this.constructor.hmacStrength, this.padLength = this.constructor.padLength / 8, this.endian = "big", this._delta8 = this.blockSize / 8, this._delta32 = this.blockSize / 32;
}
ll.BlockHash = ul, ul.prototype.update = function(e3, t2) {
  if (e3 = hl.toArray(e3, t2), this.pending ? this.pending = this.pending.concat(e3) : this.pending = e3, this.pendingTotal += e3.length, this.pending.length >= this._delta8) {
    var s2 = (e3 = this.pending).length % this._delta8;
    this.pending = e3.slice(e3.length - s2, e3.length), 0 === this.pending.length && (this.pending = null), e3 = hl.join32(e3, 0, e3.length - s2, this.endian);
    for (var i2 = 0; i2 < e3.length; i2 += this._delta32)
      this._update(e3, i2, i2 + this._delta32);
  }
  return this;
}, ul.prototype.digest = function(e3) {
  return this.update(this._pad()), cl(null === this.pending), this._digest(e3);
}, ul.prototype._pad = function() {
  var e3 = this.pendingTotal, t2 = this._delta8, s2 = t2 - (e3 + this.padLength) % t2, i2 = new Array(s2 + this.padLength);
  i2[0] = 128;
  for (var n2 = 1; n2 < s2; n2++)
    i2[n2] = 0;
  if (e3 <<= 3, "big" === this.endian) {
    for (var r2 = 8; r2 < this.padLength; r2++)
      i2[n2++] = 0;
    i2[n2++] = 0, i2[n2++] = 0, i2[n2++] = 0, i2[n2++] = 0, i2[n2++] = e3 >>> 24 & 255, i2[n2++] = e3 >>> 16 & 255, i2[n2++] = e3 >>> 8 & 255, i2[n2++] = 255 & e3;
  } else
    for (i2[n2++] = 255 & e3, i2[n2++] = e3 >>> 8 & 255, i2[n2++] = e3 >>> 16 & 255, i2[n2++] = e3 >>> 24 & 255, i2[n2++] = 0, i2[n2++] = 0, i2[n2++] = 0, i2[n2++] = 0, r2 = 8; r2 < this.padLength; r2++)
      i2[n2++] = 0;
  return i2;
};
var dl = {}, pl = Qa.rotr32;
function fl(e3, t2, s2) {
  return e3 & t2 ^ ~e3 & s2;
}
function ml(e3, t2, s2) {
  return e3 & t2 ^ e3 & s2 ^ t2 & s2;
}
function gl(e3, t2, s2) {
  return e3 ^ t2 ^ s2;
}
dl.ft_1 = function(e3, t2, s2, i2) {
  return 0 === e3 ? fl(t2, s2, i2) : 1 === e3 || 3 === e3 ? gl(t2, s2, i2) : 2 === e3 ? ml(t2, s2, i2) : void 0;
}, dl.ch32 = fl, dl.maj32 = ml, dl.p32 = gl, dl.s0_256 = function(e3) {
  return pl(e3, 2) ^ pl(e3, 13) ^ pl(e3, 22);
}, dl.s1_256 = function(e3) {
  return pl(e3, 6) ^ pl(e3, 11) ^ pl(e3, 25);
}, dl.g0_256 = function(e3) {
  return pl(e3, 7) ^ pl(e3, 18) ^ e3 >>> 3;
}, dl.g1_256 = function(e3) {
  return pl(e3, 17) ^ pl(e3, 19) ^ e3 >>> 10;
};
var yl = Qa, xl = ll, El = dl, bl = Za, vl = yl.sum32, Sl = yl.sum32_4, Al = yl.sum32_5, kl = El.ch32, Il = El.maj32, wl = El.s0_256, Pl = El.s1_256, Cl = El.g0_256, $l = El.g1_256, Nl = xl.BlockHash, _l = [1116352408, 1899447441, 3049323471, 3921009573, 961987163, 1508970993, 2453635748, 2870763221, 3624381080, 310598401, 607225278, 1426881987, 1925078388, 2162078206, 2614888103, 3248222580, 3835390401, 4022224774, 264347078, 604807628, 770255983, 1249150122, 1555081692, 1996064986, 2554220882, 2821834349, 2952996808, 3210313671, 3336571891, 3584528711, 113926993, 338241895, 666307205, 773529912, 1294757372, 1396182291, 1695183700, 1986661051, 2177026350, 2456956037, 2730485921, 2820302411, 3259730800, 3345764771, 3516065817, 3600352804, 4094571909, 275423344, 430227734, 506948616, 659060556, 883997877, 958139571, 1322822218, 1537002063, 1747873779, 1955562222, 2024104815, 2227730452, 2361852424, 2428436474, 2756734187, 3204031479, 3329325298];
function Rl() {
  if (!(this instanceof Rl))
    return new Rl();
  Nl.call(this), this.h = [1779033703, 3144134277, 1013904242, 2773480762, 1359893119, 2600822924, 528734635, 1541459225], this.k = _l, this.W = new Array(64);
}
yl.inherits(Rl, Nl);
var Ol = Rl;
Rl.blockSize = 512, Rl.outSize = 256, Rl.hmacStrength = 192, Rl.padLength = 64, Rl.prototype._update = function(e3, t2) {
  for (var s2 = this.W, i2 = 0; i2 < 16; i2++)
    s2[i2] = e3[t2 + i2];
  for (; i2 < s2.length; i2++)
    s2[i2] = Sl($l(s2[i2 - 2]), s2[i2 - 7], Cl(s2[i2 - 15]), s2[i2 - 16]);
  var n2 = this.h[0], r2 = this.h[1], o2 = this.h[2], a2 = this.h[3], l2 = this.h[4], h2 = this.h[5], c2 = this.h[6], u2 = this.h[7];
  for (bl(this.k.length === s2.length), i2 = 0; i2 < s2.length; i2++) {
    var d2 = Al(u2, Pl(l2), kl(l2, h2, c2), this.k[i2], s2[i2]), p2 = vl(wl(n2), Il(n2, r2, o2));
    u2 = c2, c2 = h2, h2 = l2, l2 = vl(a2, d2), a2 = o2, o2 = r2, r2 = n2, n2 = vl(d2, p2);
  }
  this.h[0] = vl(this.h[0], n2), this.h[1] = vl(this.h[1], r2), this.h[2] = vl(this.h[2], o2), this.h[3] = vl(this.h[3], a2), this.h[4] = vl(this.h[4], l2), this.h[5] = vl(this.h[5], h2), this.h[6] = vl(this.h[6], c2), this.h[7] = vl(this.h[7], u2);
}, Rl.prototype._digest = function(e3) {
  return "hex" === e3 ? yl.toHex32(this.h, "big") : yl.split32(this.h, "big");
};
var Dl = t(Ol);
const Ll = () => Dl();
function Tl(e3) {
  if (!e3)
    return null;
  if ("string" == typeof e3 && (e3 = JSON.parse(e3)), "" === e3.mappings)
    return { mappings: [], names: [], sources: [], version: 3 };
  const t2 = "string" == typeof e3.mappings ? i.decode(e3.mappings) : e3.mappings;
  return { ...e3, mappings: t2 };
}
async function Ml(e3, t2, s2, i2, n2) {
  Io("render chunks", 2), function(e4) {
    for (const t3 of e4)
      t3.facadeModule && t3.facadeModule.isUserDefinedEntryPoint && t3.getPreliminaryFileName();
  }(e3);
  const r2 = await Promise.all(e3.map((e4) => e4.render()));
  wo("render chunks", 2), Io("transform chunks", 2);
  const o2 = function(e4) {
    return Object.fromEntries(e4.map((e5) => {
      const t3 = e5.getRenderedChunkInfo();
      return [t3.fileName, t3];
    }));
  }(e3), { nonHashedChunksWithPlaceholders: a2, renderedChunksByPlaceholder: l2, hashDependenciesByPlaceholder: h2 } = await async function(e4, t3, s3, i3, n3) {
    const r3 = [], o3 = /* @__PURE__ */ new Map(), a3 = /* @__PURE__ */ new Map(), l3 = /* @__PURE__ */ new Set();
    for (const { preliminaryFileName: { hashPlaceholder: t4 } } of e4)
      t4 && l3.add(t4);
    return await Promise.all(e4.map(async ({ chunk: e5, preliminaryFileName: { fileName: h3, hashPlaceholder: c3 }, magicString: u2, usedModules: d2 }) => {
      const p2 = { chunk: e5, fileName: h3, ...await Vl(u2, h3, d2, t3, s3, i3, n3) }, { code: f2 } = p2;
      if (c3) {
        const { containedPlaceholders: t4, transformedCode: s4 } = ba(f2, l3), n4 = Ll().update(s4), r4 = i3.hookReduceValueSync("augmentChunkHash", "", [e5.getRenderedChunkInfo()], (e6, t5) => (t5 && (e6 += t5), e6));
        r4 && n4.update(r4), o3.set(c3, p2), a3.set(c3, { containedPlaceholders: t4, contentHash: n4.digest("hex") });
      } else
        r3.push(p2);
    })), { hashDependenciesByPlaceholder: a3, nonHashedChunksWithPlaceholders: r3, renderedChunksByPlaceholder: o3 };
  }(r2, o2, i2, s2, n2), c2 = function(e4, t3, s3) {
    const i3 = /* @__PURE__ */ new Map();
    for (const [n3, { fileName: r3 }] of e4) {
      let e5 = Ll();
      const o3 = /* @__PURE__ */ new Set([n3]);
      for (const s4 of o3) {
        const { containedPlaceholders: i4, contentHash: n4 } = t3.get(s4);
        e5.update(n4);
        for (const e6 of i4)
          o3.add(e6);
      }
      let a3, l3;
      do {
        l3 && (e5 = Ll().update(l3)), l3 = e5.digest("hex").slice(0, n3.length), a3 = Ea(r3, n3, l3);
      } while (s3[va].has(a3.toLowerCase()));
      s3[a3] = Sa, i3.set(n3, l3);
    }
    return i3;
  }(l2, h2, t2);
  !function(e4, t3, s3, i3, n3, r3) {
    for (const { chunk: i4, code: o3, fileName: a3, map: l3 } of e4.values()) {
      let e5 = xa(o3, t3);
      const h3 = xa(a3, t3);
      l3 && (l3.file = xa(l3.file, t3), e5 += Bl(h3, l3, n3, r3)), s3[h3] = i4.finalizeChunk(e5, l3, t3);
    }
    for (const { chunk: e5, code: o3, fileName: a3, map: l3 } of i3) {
      let i4 = t3.size > 0 ? xa(o3, t3) : o3;
      l3 && (i4 += Bl(a3, l3, n3, r3)), s3[a3] = e5.finalizeChunk(i4, l3, t3);
    }
  }(l2, c2, t2, a2, s2, i2), wo("transform chunks", 2);
}
async function Vl(e3, t2, s2, i2, n2, r2, o2) {
  let a2 = null;
  const h2 = [];
  let c2 = await r2.hookReduceArg0("renderChunk", [e3.toString(), i2[t2], n2, { chunks: i2 }], (e4, t3, s3) => {
    if (null == t3)
      return e4;
    if ("string" == typeof t3 && (t3 = { code: t3, map: void 0 }), null !== t3.map) {
      const e5 = Tl(t3.map);
      h2.push(e5 || { missing: true, plugin: s3.name });
    }
    return t3.code;
  });
  const { compact: u2, dir: d2, file: p2, sourcemap: f2, sourcemapExcludeSources: m2, sourcemapFile: g2, sourcemapPathTransform: y2, sourcemapIgnoreList: x2 } = n2;
  if (u2 || "\n" === c2[c2.length - 1] || (c2 += "\n"), f2) {
    let i3;
    Io("sourcemaps", 3), i3 = p2 ? N(g2 || p2) : d2 ? N(d2, t2) : N(t2);
    a2 = function(e4, t3, s3, i4, n3, r3) {
      const o3 = Ya(r3), a3 = s3.filter((e5) => !e5.excludeFromSourcemap).map((e5) => Xa(e5.id, e5.originalCode, e5.originalSourcemap, e5.sourcemapChain, o3)), h3 = new Ka(t3, a3), c3 = i4.reduce(o3, h3);
      let { sources: u3, sourcesContent: d3, names: p3, mappings: f3 } = c3.traceMappings();
      if (e4) {
        const t4 = P(e4);
        u3 = u3.map((e5) => $(t4, e5)), e4 = w(e4);
      }
      return d3 = n3 ? null : d3, new l({ file: e4, mappings: f3, names: p3, sources: u3, sourcesContent: d3 });
    }(i3, e3.generateDecodedMap({}), s2, h2, m2, o2);
    for (let e4 = 0; e4 < a2.sources.length; ++e4) {
      let t3 = a2.sources[e4];
      const s3 = `${i3}.map`, n3 = x2(t3, s3);
      "boolean" != typeof n3 && Ye(Kt("sourcemapIgnoreList function must return a boolean.")), n3 && (void 0 === a2.x_google_ignoreList && (a2.x_google_ignoreList = []), a2.x_google_ignoreList.includes(e4) || a2.x_google_ignoreList.push(e4)), y2 && (t3 = y2(t3, s3), "string" != typeof t3 && Ye(Kt("sourcemapPathTransform function must return a string."))), a2.sources[e4] = I(t3);
    }
    wo("sourcemaps", 3);
  }
  return { code: c2, map: a2 };
}
function Bl(e3, t2, s2, { sourcemap: i2, sourcemapBaseUrl: n2 }) {
  let r2;
  if ("inline" === i2)
    r2 = t2.toUrl();
  else {
    const i3 = `${w(e3)}.map`;
    r2 = n2 ? new URL(i3, n2).toString() : i3, s2.emitFile({ fileName: `${e3}.map`, source: t2.toString(), type: "asset" });
  }
  return "hidden" === i2 ? "" : `//# ${Bs}=${r2}
`;
}
class zl {
  constructor(e3, t2, s2, i2, n2) {
    this.outputOptions = e3, this.unsetOptions = t2, this.inputOptions = s2, this.pluginDriver = i2, this.graph = n2, this.facadeChunkByModule = /* @__PURE__ */ new Map(), this.includedNamespaces = /* @__PURE__ */ new Set();
  }
  async generate(e3) {
    Io("GENERATE", 1);
    const t2 = /* @__PURE__ */ Object.create(null), s2 = ((e4) => {
      const t3 = /* @__PURE__ */ new Set();
      return new Proxy(e4, { deleteProperty: (e5, s3) => ("string" == typeof s3 && t3.delete(s3.toLowerCase()), Reflect.deleteProperty(e5, s3)), get: (e5, s3) => s3 === va ? t3 : Reflect.get(e5, s3), set: (e5, s3, i2) => ("string" == typeof s3 && t3.add(s3.toLowerCase()), Reflect.set(e5, s3, i2)) });
    })(t2);
    this.pluginDriver.setOutputBundle(s2, this.outputOptions);
    try {
      Io("initialize render", 2), await this.pluginDriver.hookParallel("renderStart", [this.outputOptions, this.inputOptions]), wo("initialize render", 2), Io("generate chunks", 2);
      const e4 = (() => {
        let e5 = 0;
        return (t4, s3 = 8) => {
          if (s3 > 64)
            return Ye(Kt(`Hashes cannot be longer than 64 characters, received ${s3}. Check the "${t4}" option.`));
          const i2 = `${ma}${Oi(++e5).padStart(s3 - 5, "0")}${ga}`;
          return i2.length > s3 ? Ye(Kt(`To generate hashes for this number of chunks (currently ${e5}), you need a minimum hash size of ${i2.length}, received ${s3}. Check the "${t4}" option.`)) : i2;
        };
      })(), t3 = await this.generateChunks(s2, e4);
      t3.length > 1 && function(e5, t4) {
        if ("umd" === e5.format || "iife" === e5.format)
          return Ye(zt("output.format", ze, "UMD and IIFE output formats are not supported for code-splitting builds", e5.format));
        if ("string" == typeof e5.file)
          return Ye(zt("output.file", Me, 'when building multiple chunks, the "output.dir" option must be used, not "output.file". To inline dynamic imports, set the "inlineDynamicImports" option'));
        if (e5.sourcemapFile)
          return Ye(zt("output.sourcemapFile", He, '"output.sourcemapFile" is only supported for single-file builds'));
        !e5.amd.autoId && e5.amd.id && t4(ve, zt("output.amd.id", Te, 'this option is only properly supported for single-file builds. Use "output.amd.autoId" and "output.amd.basePath" instead'));
      }(this.outputOptions, this.inputOptions.onLog), this.pluginDriver.setChunkInformation(this.facadeChunkByModule);
      for (const e5 of t3)
        e5.generateExports();
      wo("generate chunks", 2), await Ml(t3, s2, this.pluginDriver, this.outputOptions, this.inputOptions.onLog);
    } catch (e4) {
      throw await this.pluginDriver.hookParallel("renderError", [e4]), e4;
    }
    return ((e4) => {
      const t3 = /* @__PURE__ */ new Set(), s3 = Object.values(e4);
      for (const e5 of s3)
        "asset" === e5.type && e5.needsCodeReference && t3.add(e5.fileName);
      for (const e5 of s3)
        if ("chunk" === e5.type)
          for (const s4 of e5.referencedFiles)
            t3.has(s4) && t3.delete(s4);
      for (const s4 of t3)
        delete e4[s4];
    })(s2), Io("generate bundle", 2), await this.pluginDriver.hookSeq("generateBundle", [this.outputOptions, s2, e3]), this.finaliseAssets(s2), wo("generate bundle", 2), wo("GENERATE", 1), t2;
  }
  async addManualChunks(e3) {
    const t2 = /* @__PURE__ */ new Map(), s2 = await Promise.all(Object.entries(e3).map(async ([e4, t3]) => ({ alias: e4, entries: await this.graph.moduleLoader.addAdditionalModules(t3) })));
    for (const { alias: e4, entries: i2 } of s2)
      for (const s3 of i2)
        Fl(e4, s3, t2);
    return t2;
  }
  assignManualChunks(e3) {
    const t2 = [], s2 = { getModuleIds: () => this.graph.modulesById.keys(), getModuleInfo: this.graph.getModuleInfo };
    for (const i3 of this.graph.modulesById.values())
      if (i3 instanceof Oo) {
        const n2 = e3(i3.id, s2);
        "string" == typeof n2 && t2.push([n2, i3]);
      }
    t2.sort(([e4], [t3]) => e4 > t3 ? 1 : e4 < t3 ? -1 : 0);
    const i2 = /* @__PURE__ */ new Map();
    for (const [e4, s3] of t2)
      Fl(e4, s3, i2);
    return i2;
  }
  finaliseAssets(e3) {
    if (this.outputOptions.validate) {
      for (const t2 of Object.values(e3))
        if ("code" in t2)
          try {
            this.graph.contextParse(t2.code, { ecmaVersion: "latest" });
          } catch (e4) {
            this.inputOptions.onLog(ve, Ot(t2, e4));
          }
    }
    this.pluginDriver.finaliseAssets();
  }
  async generateChunks(e3, t2) {
    const { experimentalMinChunkSize: s2, inlineDynamicImports: i2, manualChunks: n2, preserveModules: r2 } = this.outputOptions, o2 = "object" == typeof n2 ? await this.addManualChunks(n2) : this.assignManualChunks(n2), a2 = function({ compact: e4, generatedCode: { arrowFunctions: t3, constBindings: s3, objectShorthand: i3, reservedNamesAsProps: n3 } }) {
      const { _: r3, n: o3, s: a3 } = e4 ? { _: "", n: "", s: "" } : { _: " ", n: "\n", s: ";" }, l3 = s3 ? "const" : "var", h3 = (e5, { isAsync: t4, name: s4 }) => `${t4 ? "async " : ""}function${s4 ? ` ${s4}` : ""}${r3}(${e5.join(`,${r3}`)})${r3}`, c3 = t3 ? (e5, { isAsync: t4, name: s4 }) => {
        const i4 = 1 === e5.length;
        return `${s4 ? `${l3} ${s4}${r3}=${r3}` : ""}${t4 ? `async${i4 ? " " : r3}` : ""}${i4 ? e5[0] : `(${e5.join(`,${r3}`)})`}${r3}=>${r3}`;
      } : h3, u3 = (e5, { functionReturn: s4, lineBreakIndent: i4, name: n4 }) => [`${c3(e5, { isAsync: false, name: n4 })}${t3 ? i4 ? `${o3}${i4.base}${i4.t}` : "" : `{${i4 ? `${o3}${i4.base}${i4.t}` : r3}${s4 ? "return " : ""}`}`, t3 ? `${n4 ? ";" : ""}${i4 ? `${o3}${i4.base}` : ""}` : `${a3}${i4 ? `${o3}${i4.base}` : r3}}`], d3 = n3 ? (e5) => qa.test(e5) : (e5) => !ye.has(e5) && qa.test(e5);
      return { _: r3, cnst: l3, getDirectReturnFunction: u3, getDirectReturnIifeLeft: (e5, s4, { needsArrowReturnParens: i4, needsWrappedFunction: n4 }) => {
        const [r4, o4] = u3(e5, { functionReturn: true, lineBreakIndent: null, name: null });
        return `${Wa(`${r4}${Wa(s4, t3 && i4)}${o4}`, t3 || n4)}(`;
      }, getFunctionIntro: c3, getNonArrowFunctionIntro: h3, getObject(e5, { lineBreakIndent: t4 }) {
        const s4 = t4 ? `${o3}${t4.base}${t4.t}` : r3;
        return `{${e5.map(([e6, t5]) => {
          if (null === e6)
            return `${s4}${t5}`;
          const n4 = !d3(e6);
          return e6 === t5 && i3 && !n4 ? s4 + e6 : `${s4}${n4 ? `'${e6}'` : e6}:${r3}${t5}`;
        }).join(",")}${0 === e5.length ? "" : t4 ? `${o3}${t4.base}` : r3}}`;
      }, getPropertyAccess: (e5) => d3(e5) ? `.${e5}` : `[${JSON.stringify(e5)}]`, n: o3, s: a3 };
    }(this.outputOptions), l2 = function(e4) {
      const t3 = [];
      for (const s3 of e4.values())
        s3 instanceof Oo && (s3.isIncluded() || s3.info.isEntry || s3.includedDynamicImporters.length > 0) && t3.push(s3);
      return t3;
    }(this.graph.modulesById), h2 = function(e4) {
      if (0 === e4.length)
        return "/";
      if (1 === e4.length)
        return P(e4[0]);
      const t3 = e4.slice(1).reduce((e5, t4) => {
        const s3 = t4.split(/\/+|\\+/);
        let i3;
        for (i3 = 0; e5[i3] === s3[i3] && i3 < Math.min(e5.length, s3.length); i3++)
          ;
        return e5.slice(0, i3);
      }, e4[0].split(/\/+|\\+/));
      return t3.length > 1 ? t3.join("/") : "/";
    }(function(e4, t3) {
      const s3 = [];
      for (const i3 of e4)
        (i3.info.isEntry || t3) && A(i3.id) && s3.push(i3.id);
      return s3;
    }(l2, r2)), c2 = function(e4, t3, s3) {
      const i3 = /* @__PURE__ */ new Map();
      for (const n3 of e4.values())
        n3 instanceof Qt && i3.set(n3, new z(n3, t3, s3));
      return i3;
    }(this.graph.modulesById, this.outputOptions, h2), u2 = [], d2 = /* @__PURE__ */ new Map();
    for (const { alias: n3, modules: p3 } of i2 ? [{ alias: null, modules: l2 }] : r2 ? l2.map((e4) => ({ alias: null, modules: [e4] })) : Da(this.graph.entryModules, o2, s2, this.inputOptions.onLog)) {
      p3.sort(Ua);
      const s3 = new Pa(p3, this.inputOptions, this.outputOptions, this.unsetOptions, this.pluginDriver, this.graph.modulesById, d2, c2, this.facadeChunkByModule, this.includedNamespaces, n3, t2, e3, h2, a2);
      u2.push(s3);
    }
    for (const e4 of u2)
      e4.link();
    const p2 = [];
    for (const e4 of u2)
      p2.push(...e4.generateFacades());
    return [...u2, ...p2];
  }
}
function Fl(e3, t2, s2) {
  const i2 = s2.get(t2);
  if ("string" == typeof i2 && i2 !== e3)
    return Ye((n2 = t2.id, r2 = e3, o2 = i2, { code: lt, message: `Cannot assign "${T(n2)}" to the "${r2}" chunk as it is already in the "${o2}" chunk.` }));
  var n2, r2, o2;
  s2.set(t2, e3);
}
var jl = [509, 0, 227, 0, 150, 4, 294, 9, 1368, 2, 2, 1, 6, 3, 41, 2, 5, 0, 166, 1, 574, 3, 9, 9, 370, 1, 81, 2, 71, 10, 50, 3, 123, 2, 54, 14, 32, 10, 3, 1, 11, 3, 46, 10, 8, 0, 46, 9, 7, 2, 37, 13, 2, 9, 6, 1, 45, 0, 13, 2, 49, 13, 9, 3, 2, 11, 83, 11, 7, 0, 3, 0, 158, 11, 6, 9, 7, 3, 56, 1, 2, 6, 3, 1, 3, 2, 10, 0, 11, 1, 3, 6, 4, 4, 193, 17, 10, 9, 5, 0, 82, 19, 13, 9, 214, 6, 3, 8, 28, 1, 83, 16, 16, 9, 82, 12, 9, 9, 84, 14, 5, 9, 243, 14, 166, 9, 71, 5, 2, 1, 3, 3, 2, 0, 2, 1, 13, 9, 120, 6, 3, 6, 4, 0, 29, 9, 41, 6, 2, 3, 9, 0, 10, 10, 47, 15, 406, 7, 2, 7, 17, 9, 57, 21, 2, 13, 123, 5, 4, 0, 2, 1, 2, 6, 2, 0, 9, 9, 49, 4, 2, 1, 2, 4, 9, 9, 330, 3, 10, 1, 2, 0, 49, 6, 4, 4, 14, 9, 5351, 0, 7, 14, 13835, 9, 87, 9, 39, 4, 60, 6, 26, 9, 1014, 0, 2, 54, 8, 3, 82, 0, 12, 1, 19628, 1, 4706, 45, 3, 22, 543, 4, 4, 5, 9, 7, 3, 6, 31, 3, 149, 2, 1418, 49, 513, 54, 5, 49, 9, 0, 15, 0, 23, 4, 2, 14, 1361, 6, 2, 16, 3, 6, 2, 1, 2, 4, 101, 0, 161, 6, 10, 9, 357, 0, 62, 13, 499, 13, 983, 6, 110, 6, 6, 9, 4759, 9, 787719, 239], Ul = [0, 11, 2, 25, 2, 18, 2, 1, 2, 14, 3, 13, 35, 122, 70, 52, 268, 28, 4, 48, 48, 31, 14, 29, 6, 37, 11, 29, 3, 35, 5, 7, 2, 4, 43, 157, 19, 35, 5, 35, 5, 39, 9, 51, 13, 10, 2, 14, 2, 6, 2, 1, 2, 10, 2, 14, 2, 6, 2, 1, 68, 310, 10, 21, 11, 7, 25, 5, 2, 41, 2, 8, 70, 5, 3, 0, 2, 43, 2, 1, 4, 0, 3, 22, 11, 22, 10, 30, 66, 18, 2, 1, 11, 21, 11, 25, 71, 55, 7, 1, 65, 0, 16, 3, 2, 2, 2, 28, 43, 28, 4, 28, 36, 7, 2, 27, 28, 53, 11, 21, 11, 18, 14, 17, 111, 72, 56, 50, 14, 50, 14, 35, 349, 41, 7, 1, 79, 28, 11, 0, 9, 21, 43, 17, 47, 20, 28, 22, 13, 52, 58, 1, 3, 0, 14, 44, 33, 24, 27, 35, 30, 0, 3, 0, 9, 34, 4, 0, 13, 47, 15, 3, 22, 0, 2, 0, 36, 17, 2, 24, 20, 1, 64, 6, 2, 0, 2, 3, 2, 14, 2, 9, 8, 46, 39, 7, 3, 1, 3, 21, 2, 6, 2, 1, 2, 4, 4, 0, 19, 0, 13, 4, 159, 52, 19, 3, 21, 2, 31, 47, 21, 1, 2, 0, 185, 46, 42, 3, 37, 47, 21, 0, 60, 42, 14, 0, 72, 26, 38, 6, 186, 43, 117, 63, 32, 7, 3, 0, 3, 7, 2, 1, 2, 23, 16, 0, 2, 0, 95, 7, 3, 38, 17, 0, 2, 0, 29, 0, 11, 39, 8, 0, 22, 0, 12, 45, 20, 0, 19, 72, 264, 8, 2, 36, 18, 0, 50, 29, 113, 6, 2, 1, 2, 37, 22, 0, 26, 5, 2, 1, 2, 31, 15, 0, 328, 18, 16, 0, 2, 12, 2, 33, 125, 0, 80, 921, 103, 110, 18, 195, 2637, 96, 16, 1071, 18, 5, 4026, 582, 8634, 568, 8, 30, 18, 78, 18, 29, 19, 47, 17, 3, 32, 20, 6, 18, 689, 63, 129, 74, 6, 0, 67, 12, 65, 1, 2, 0, 29, 6135, 9, 1237, 43, 8, 8936, 3, 2, 6, 2, 1, 2, 290, 16, 0, 30, 2, 3, 0, 15, 3, 9, 395, 2309, 106, 6, 12, 4, 8, 8, 9, 5991, 84, 2, 70, 2, 1, 3, 0, 3, 1, 3, 3, 2, 11, 2, 0, 2, 6, 2, 64, 2, 3, 3, 7, 2, 6, 2, 27, 2, 3, 2, 4, 2, 0, 4, 6, 2, 339, 3, 24, 2, 24, 2, 30, 2, 24, 2, 30, 2, 24, 2, 30, 2, 24, 2, 30, 2, 24, 2, 7, 1845, 30, 7, 5, 262, 61, 147, 44, 11, 6, 17, 0, 322, 29, 19, 43, 485, 27, 757, 6, 2, 3, 2, 1, 2, 14, 2, 196, 60, 67, 8, 0, 1205, 3, 2, 26, 2, 1, 2, 0, 3, 0, 2, 9, 2, 3, 2, 0, 2, 0, 7, 0, 5, 0, 2, 0, 2, 0, 2, 2, 2, 1, 2, 0, 3, 0, 2, 0, 2, 0, 2, 0, 2, 0, 2, 1, 2, 0, 3, 3, 2, 6, 2, 3, 2, 3, 2, 0, 2, 9, 2, 16, 6, 2, 2, 4, 2, 16, 4421, 42719, 33, 4153, 7, 221, 3, 5761, 15, 7472, 3104, 541, 1507, 4938, 6, 4191], Gl = "ªµºÀ-ÖØ-öø-ˁˆ-ˑˠ-ˤˬˮͰ-ʹͶͷͺ-ͽͿΆΈ-ΊΌΎ-ΡΣ-ϵϷ-ҁҊ-ԯԱ-Ֆՙՠ-ֈא-תׯ-ײؠ-يٮٯٱ-ۓەۥۦۮۯۺ-ۼۿܐܒ-ܯݍ-ޥޱߊ-ߪߴߵߺࠀ-ࠕࠚࠤࠨࡀ-ࡘࡠ-ࡪࡰ-ࢇࢉ-ࢎࢠ-ࣉऄ-हऽॐक़-ॡॱ-ঀঅ-ঌএঐও-নপ-রলশ-হঽৎড়ঢ়য়-ৡৰৱৼਅ-ਊਏਐਓ-ਨਪ-ਰਲਲ਼ਵਸ਼ਸਹਖ਼-ੜਫ਼ੲ-ੴઅ-ઍએ-ઑઓ-નપ-રલળવ-હઽૐૠૡૹଅ-ଌଏଐଓ-ନପ-ରଲଳଵ-ହଽଡ଼ଢ଼ୟ-ୡୱஃஅ-ஊஎ-ஐஒ-கஙசஜஞடணதந-பம-ஹௐఅ-ఌఎ-ఐఒ-నప-హఽౘ-ౚౝౠౡಀಅ-ಌಎ-ಐಒ-ನಪ-ಳವ-ಹಽೝೞೠೡೱೲഄ-ഌഎ-ഐഒ-ഺഽൎൔ-ൖൟ-ൡൺ-ൿඅ-ඖක-නඳ-රලව-ෆก-ะาำเ-ๆກຂຄຆ-ຊຌ-ຣລວ-ະາຳຽເ-ໄໆໜ-ໟༀཀ-ཇཉ-ཬྈ-ྌက-ဪဿၐ-ၕၚ-ၝၡၥၦၮ-ၰၵ-ႁႎႠ-ჅჇჍა-ჺჼ-ቈቊ-ቍቐ-ቖቘቚ-ቝበ-ኈኊ-ኍነ-ኰኲ-ኵኸ-ኾዀዂ-ዅወ-ዖዘ-ጐጒ-ጕጘ-ፚᎀ-ᎏᎠ-Ᏽᏸ-ᏽᐁ-ᙬᙯ-ᙿᚁ-ᚚᚠ-ᛪᛮ-ᛸᜀ-ᜑᜟ-ᜱᝀ-ᝑᝠ-ᝬᝮ-ᝰក-ឳៗៜᠠ-ᡸᢀ-ᢨᢪᢰ-ᣵᤀ-ᤞᥐ-ᥭᥰ-ᥴᦀ-ᦫᦰ-ᧉᨀ-ᨖᨠ-ᩔᪧᬅ-ᬳᭅ-ᭌᮃ-ᮠᮮᮯᮺ-ᯥᰀ-ᰣᱍ-ᱏᱚ-ᱽᲀ-ᲈᲐ-ᲺᲽ-Ჿᳩ-ᳬᳮ-ᳳᳵᳶᳺᴀ-ᶿḀ-ἕἘ-Ἕἠ-ὅὈ-Ὅὐ-ὗὙὛὝὟ-ώᾀ-ᾴᾶ-ᾼιῂ-ῄῆ-ῌῐ-ΐῖ-Ίῠ-Ῥῲ-ῴῶ-ῼⁱⁿₐ-ₜℂℇℊ-ℓℕ℘-ℝℤΩℨK-ℹℼ-ℿⅅ-ⅉⅎⅠ-ↈⰀ-ⳤⳫ-ⳮⳲⳳⴀ-ⴥⴧⴭⴰ-ⵧⵯⶀ-ⶖⶠ-ⶦⶨ-ⶮⶰ-ⶶⶸ-ⶾⷀ-ⷆⷈ-ⷎⷐ-ⷖⷘ-ⷞ々-〇〡-〩〱-〵〸-〼ぁ-ゖ゛-ゟァ-ヺー-ヿㄅ-ㄯㄱ-ㆎㆠ-ㆿㇰ-ㇿ㐀-䶿一-ꒌꓐ-ꓽꔀ-ꘌꘐ-ꘟꘪꘫꙀ-ꙮꙿ-ꚝꚠ-ꛯꜗ-ꜟꜢ-ꞈꞋ-ꟊꟐꟑꟓꟕ-ꟙꟲ-ꠁꠃ-ꠅꠇ-ꠊꠌ-ꠢꡀ-ꡳꢂ-ꢳꣲ-ꣷꣻꣽꣾꤊ-ꤥꤰ-ꥆꥠ-ꥼꦄ-ꦲꧏꧠ-ꧤꧦ-ꧯꧺ-ꧾꨀ-ꨨꩀ-ꩂꩄ-ꩋꩠ-ꩶꩺꩾ-ꪯꪱꪵꪶꪹ-ꪽꫀꫂꫛ-ꫝꫠ-ꫪꫲ-ꫴꬁ-ꬆꬉ-ꬎꬑ-ꬖꬠ-ꬦꬨ-ꬮꬰ-ꭚꭜ-ꭩꭰ-ꯢ가-힣ힰ-ퟆퟋ-ퟻ豈-舘並-龎ﬀ-ﬆﬓ-ﬗיִײַ-ﬨשׁ-זּטּ-לּמּנּסּףּפּצּ-ﮱﯓ-ﴽﵐ-ﶏﶒ-ﷇﷰ-ﷻﹰ-ﹴﹶ-ﻼＡ-Ｚａ-ｚｦ-ﾾￂ-ￇￊ-ￏￒ-ￗￚ-ￜ", Wl = { 3: "abstract boolean byte char class double enum export extends final float goto implements import int interface long native package private protected public short static super synchronized throws transient volatile", 5: "class enum extends super const export import", 6: "enum", strict: "implements interface let package private protected public static yield", strictBind: "eval arguments" }, ql = "break case catch continue debugger default do else finally for function if return switch throw try var while with null true false instanceof typeof void delete new in this", Hl = { 5: ql, "5module": ql + " export import", 6: ql + " const class extends export import super" }, Kl = /^in(stanceof)?$/, Yl = new RegExp("[" + Gl + "]"), Xl = new RegExp("[" + Gl + "‌‍·̀-ͯ·҃-֑҇-ׇֽֿׁׂׅׄؐ-ًؚ-٩ٰۖ-ۜ۟-۪ۤۧۨ-ۭ۰-۹ܑܰ-݊ަ-ް߀-߉߫-߽߳ࠖ-࠙ࠛ-ࠣࠥ-ࠧࠩ-࡙࠭-࡛࢘-࢟࣊-ࣣ࣡-ःऺ-़ा-ॏ॑-ॗॢॣ०-९ঁ-ঃ়া-ৄেৈো-্ৗৢৣ০-৯৾ਁ-ਃ਼ਾ-ੂੇੈੋ-੍ੑ੦-ੱੵઁ-ઃ઼ા-ૅે-ૉો-્ૢૣ૦-૯ૺ-૿ଁ-ଃ଼ା-ୄେୈୋ-୍୕-ୗୢୣ୦-୯ஂா-ூெ-ைொ-்ௗ௦-௯ఀ-ఄ఼ా-ౄె-ైొ-్ౕౖౢౣ౦-౯ಁ-ಃ಼ಾ-ೄೆ-ೈೊ-್ೕೖೢೣ೦-೯ೳഀ-ഃ഻഼ാ-ൄെ-ൈൊ-്ൗൢൣ൦-൯ඁ-ඃ්ා-ුූෘ-ෟ෦-෯ෲෳัิ-ฺ็-๎๐-๙ັິ-ຼ່-໎໐-໙༘༙༠-༩༹༵༷༾༿ཱ-྄྆྇ྍ-ྗྙ-ྼ࿆ါ-ှ၀-၉ၖ-ၙၞ-ၠၢ-ၤၧ-ၭၱ-ၴႂ-ႍႏ-ႝ፝-፟፩-፱ᜒ-᜕ᜲ-᜴ᝒᝓᝲᝳ឴-៓៝០-៩᠋-᠍᠏-᠙ᢩᤠ-ᤫᤰ-᤻᥆-᥏᧐-᧚ᨗ-ᨛᩕ-ᩞ᩠-᩿᩼-᪉᪐-᪙᪰-᪽ᪿ-ᫎᬀ-ᬄ᬴-᭄᭐-᭙᭫-᭳ᮀ-ᮂᮡ-ᮭ᮰-᮹᯦-᯳ᰤ-᰷᱀-᱉᱐-᱙᳐-᳔᳒-᳨᳭᳴᳷-᳹᷀-᷿‿⁀⁔⃐-⃥⃜⃡-⃰⳯-⵿⳱ⷠ-〪ⷿ-゙゚〯꘠-꘩꙯ꙴ-꙽ꚞꚟ꛰꛱ꠂ꠆ꠋꠣ-ꠧ꠬ꢀꢁꢴ-ꣅ꣐-꣙꣠-꣱ꣿ-꤉ꤦ-꤭ꥇ-꥓ꦀ-ꦃ꦳-꧀꧐-꧙ꧥ꧰-꧹ꨩ-ꨶꩃꩌꩍ꩐-꩙ꩻ-ꩽꪰꪲ-ꪴꪷꪸꪾ꪿꫁ꫫ-ꫯꫵ꫶ꯣ-ꯪ꯬꯭꯰-꯹ﬞ︀-️︠-︯︳︴﹍-﹏０-９＿]");
function Ql(e3, t2) {
  for (var s2 = 65536, i2 = 0; i2 < t2.length; i2 += 2) {
    if ((s2 += t2[i2]) > e3)
      return false;
    if ((s2 += t2[i2 + 1]) >= e3)
      return true;
  }
  return false;
}
function Zl(e3, t2) {
  return e3 < 65 ? 36 === e3 : e3 < 91 || (e3 < 97 ? 95 === e3 : e3 < 123 || (e3 <= 65535 ? e3 >= 170 && Yl.test(String.fromCharCode(e3)) : false !== t2 && Ql(e3, Ul)));
}
function Jl(e3, t2) {
  return e3 < 48 ? 36 === e3 : e3 < 58 || !(e3 < 65) && (e3 < 91 || (e3 < 97 ? 95 === e3 : e3 < 123 || (e3 <= 65535 ? e3 >= 170 && Xl.test(String.fromCharCode(e3)) : false !== t2 && (Ql(e3, Ul) || Ql(e3, jl)))));
}
var eh = function(e3, t2) {
  void 0 === t2 && (t2 = {}), this.label = e3, this.keyword = t2.keyword, this.beforeExpr = !!t2.beforeExpr, this.startsExpr = !!t2.startsExpr, this.isLoop = !!t2.isLoop, this.isAssign = !!t2.isAssign, this.prefix = !!t2.prefix, this.postfix = !!t2.postfix, this.binop = t2.binop || null, this.updateContext = null;
};
function th(e3, t2) {
  return new eh(e3, { beforeExpr: true, binop: t2 });
}
var sh = { beforeExpr: true }, ih = { startsExpr: true }, nh = {};
function rh(e3, t2) {
  return void 0 === t2 && (t2 = {}), t2.keyword = e3, nh[e3] = new eh(e3, t2);
}
var oh = { num: new eh("num", ih), regexp: new eh("regexp", ih), string: new eh("string", ih), name: new eh("name", ih), privateId: new eh("privateId", ih), eof: new eh("eof"), bracketL: new eh("[", { beforeExpr: true, startsExpr: true }), bracketR: new eh("]"), braceL: new eh("{", { beforeExpr: true, startsExpr: true }), braceR: new eh("}"), parenL: new eh("(", { beforeExpr: true, startsExpr: true }), parenR: new eh(")"), comma: new eh(",", sh), semi: new eh(";", sh), colon: new eh(":", sh), dot: new eh("."), question: new eh("?", sh), questionDot: new eh("?."), arrow: new eh("=>", sh), template: new eh("template"), invalidTemplate: new eh("invalidTemplate"), ellipsis: new eh("...", sh), backQuote: new eh("`", ih), dollarBraceL: new eh("${", { beforeExpr: true, startsExpr: true }), eq: new eh("=", { beforeExpr: true, isAssign: true }), assign: new eh("_=", { beforeExpr: true, isAssign: true }), incDec: new eh("++/--", { prefix: true, postfix: true, startsExpr: true }), prefix: new eh("!/~", { beforeExpr: true, prefix: true, startsExpr: true }), logicalOR: th("||", 1), logicalAND: th("&&", 2), bitwiseOR: th("|", 3), bitwiseXOR: th("^", 4), bitwiseAND: th("&", 5), equality: th("==/!=/===/!==", 6), relational: th("</>/<=/>=", 7), bitShift: th("<</>>/>>>", 8), plusMin: new eh("+/-", { beforeExpr: true, binop: 9, prefix: true, startsExpr: true }), modulo: th("%", 10), star: th("*", 10), slash: th("/", 10), starstar: new eh("**", { beforeExpr: true }), coalesce: th("??", 1), _break: rh("break"), _case: rh("case", sh), _catch: rh("catch"), _continue: rh("continue"), _debugger: rh("debugger"), _default: rh("default", sh), _do: rh("do", { isLoop: true, beforeExpr: true }), _else: rh("else", sh), _finally: rh("finally"), _for: rh("for", { isLoop: true }), _function: rh("function", ih), _if: rh("if"), _return: rh("return", sh), _switch: rh("switch"), _throw: rh("throw", sh), _try: rh("try"), _var: rh("var"), _const: rh("const"), _while: rh("while", { isLoop: true }), _with: rh("with"), _new: rh("new", { beforeExpr: true, startsExpr: true }), _this: rh("this", ih), _super: rh("super", ih), _class: rh("class", ih), _extends: rh("extends", sh), _export: rh("export"), _import: rh("import", ih), _null: rh("null", ih), _true: rh("true", ih), _false: rh("false", ih), _in: rh("in", { beforeExpr: true, binop: 7 }), _instanceof: rh("instanceof", { beforeExpr: true, binop: 7 }), _typeof: rh("typeof", { beforeExpr: true, prefix: true, startsExpr: true }), _void: rh("void", { beforeExpr: true, prefix: true, startsExpr: true }), _delete: rh("delete", { beforeExpr: true, prefix: true, startsExpr: true }) }, ah = /\r\n?|\n|\u2028|\u2029/, lh = new RegExp(ah.source, "g");
function hh(e3) {
  return 10 === e3 || 13 === e3 || 8232 === e3 || 8233 === e3;
}
function ch(e3, t2, s2) {
  void 0 === s2 && (s2 = e3.length);
  for (var i2 = t2; i2 < s2; i2++) {
    var n2 = e3.charCodeAt(i2);
    if (hh(n2))
      return i2 < s2 - 1 && 13 === n2 && 10 === e3.charCodeAt(i2 + 1) ? i2 + 2 : i2 + 1;
  }
  return -1;
}
var uh = /[\u1680\u2000-\u200a\u202f\u205f\u3000\ufeff]/, dh = /(?:\s|\/\/.*|\/\*[^]*?\*\/)*/g, ph = Object.prototype, fh = ph.hasOwnProperty, mh = ph.toString, gh = Object.hasOwn || function(e3, t2) {
  return fh.call(e3, t2);
}, yh = Array.isArray || function(e3) {
  return "[object Array]" === mh.call(e3);
};
function xh(e3) {
  return new RegExp("^(?:" + e3.replace(/ /g, "|") + ")$");
}
function Eh(e3) {
  return e3 <= 65535 ? String.fromCharCode(e3) : (e3 -= 65536, String.fromCharCode(55296 + (e3 >> 10), 56320 + (1023 & e3)));
}
var bh = /(?:[\uD800-\uDBFF](?![\uDC00-\uDFFF])|(?:[^\uD800-\uDBFF]|^)[\uDC00-\uDFFF])/, vh = function(e3, t2) {
  this.line = e3, this.column = t2;
};
vh.prototype.offset = function(e3) {
  return new vh(this.line, this.column + e3);
};
var Sh = function(e3, t2, s2) {
  this.start = t2, this.end = s2, null !== e3.sourceFile && (this.source = e3.sourceFile);
};
function Ah(e3, t2) {
  for (var s2 = 1, i2 = 0; ; ) {
    var n2 = ch(e3, i2, t2);
    if (n2 < 0)
      return new vh(s2, t2 - i2);
    ++s2, i2 = n2;
  }
}
var kh = { ecmaVersion: null, sourceType: "script", onInsertedSemicolon: null, onTrailingComma: null, allowReserved: null, allowReturnOutsideFunction: false, allowImportExportEverywhere: false, allowAwaitOutsideFunction: null, allowSuperOutsideMethod: null, allowHashBang: false, locations: false, onToken: null, onComment: null, ranges: false, program: null, sourceFile: null, directSourceFile: null, preserveParens: false }, Ih = false;
function wh(e3) {
  var t2 = {};
  for (var s2 in kh)
    t2[s2] = e3 && gh(e3, s2) ? e3[s2] : kh[s2];
  if ("latest" === t2.ecmaVersion ? t2.ecmaVersion = 1e8 : null == t2.ecmaVersion ? (!Ih && "object" == typeof console && console.warn && (Ih = true, console.warn("Since Acorn 8.0.0, options.ecmaVersion is required.\nDefaulting to 2020, but this will stop working in the future.")), t2.ecmaVersion = 11) : t2.ecmaVersion >= 2015 && (t2.ecmaVersion -= 2009), null == t2.allowReserved && (t2.allowReserved = t2.ecmaVersion < 5), e3 && null != e3.allowHashBang || (t2.allowHashBang = t2.ecmaVersion >= 14), yh(t2.onToken)) {
    var i2 = t2.onToken;
    t2.onToken = function(e4) {
      return i2.push(e4);
    };
  }
  return yh(t2.onComment) && (t2.onComment = function(e4, t3) {
    return function(s3, i3, n2, r2, o2, a2) {
      var l2 = { type: s3 ? "Block" : "Line", value: i3, start: n2, end: r2 };
      e4.locations && (l2.loc = new Sh(this, o2, a2)), e4.ranges && (l2.range = [n2, r2]), t3.push(l2);
    };
  }(t2, t2.onComment)), t2;
}
var Ph = 256;
function Ch(e3, t2) {
  return 2 | (e3 ? 4 : 0) | (t2 ? 8 : 0);
}
var $h = function(e3, t2, s2) {
  this.options = e3 = wh(e3), this.sourceFile = e3.sourceFile, this.keywords = xh(Hl[e3.ecmaVersion >= 6 ? 6 : "module" === e3.sourceType ? "5module" : 5]);
  var i2 = "";
  true !== e3.allowReserved && (i2 = Wl[e3.ecmaVersion >= 6 ? 6 : 5 === e3.ecmaVersion ? 5 : 3], "module" === e3.sourceType && (i2 += " await")), this.reservedWords = xh(i2);
  var n2 = (i2 ? i2 + " " : "") + Wl.strict;
  this.reservedWordsStrict = xh(n2), this.reservedWordsStrictBind = xh(n2 + " " + Wl.strictBind), this.input = String(t2), this.containsEsc = false, s2 ? (this.pos = s2, this.lineStart = this.input.lastIndexOf("\n", s2 - 1) + 1, this.curLine = this.input.slice(0, this.lineStart).split(ah).length) : (this.pos = this.lineStart = 0, this.curLine = 1), this.type = oh.eof, this.value = null, this.start = this.end = this.pos, this.startLoc = this.endLoc = this.curPosition(), this.lastTokEndLoc = this.lastTokStartLoc = null, this.lastTokStart = this.lastTokEnd = this.pos, this.context = this.initialContext(), this.exprAllowed = true, this.inModule = "module" === e3.sourceType, this.strict = this.inModule || this.strictDirective(this.pos), this.potentialArrowAt = -1, this.potentialArrowInForAwait = false, this.yieldPos = this.awaitPos = this.awaitIdentPos = 0, this.labels = [], this.undefinedExports = /* @__PURE__ */ Object.create(null), 0 === this.pos && e3.allowHashBang && "#!" === this.input.slice(0, 2) && this.skipLineComment(2), this.scopeStack = [], this.enterScope(1), this.regexpState = null, this.privateNameStack = [];
}, Nh = { inFunction: { configurable: true }, inGenerator: { configurable: true }, inAsync: { configurable: true }, canAwait: { configurable: true }, allowSuper: { configurable: true }, allowDirectSuper: { configurable: true }, treatFunctionsAsVar: { configurable: true }, allowNewDotTarget: { configurable: true }, inClassStaticBlock: { configurable: true } };
$h.prototype.parse = function() {
  var e3 = this.options.program || this.startNode();
  return this.nextToken(), this.parseTopLevel(e3);
}, Nh.inFunction.get = function() {
  return (2 & this.currentVarScope().flags) > 0;
}, Nh.inGenerator.get = function() {
  return (8 & this.currentVarScope().flags) > 0 && !this.currentVarScope().inClassFieldInit;
}, Nh.inAsync.get = function() {
  return (4 & this.currentVarScope().flags) > 0 && !this.currentVarScope().inClassFieldInit;
}, Nh.canAwait.get = function() {
  for (var e3 = this.scopeStack.length - 1; e3 >= 0; e3--) {
    var t2 = this.scopeStack[e3];
    if (t2.inClassFieldInit || t2.flags & Ph)
      return false;
    if (2 & t2.flags)
      return (4 & t2.flags) > 0;
  }
  return this.inModule && this.options.ecmaVersion >= 13 || this.options.allowAwaitOutsideFunction;
}, Nh.allowSuper.get = function() {
  var e3 = this.currentThisScope(), t2 = e3.flags, s2 = e3.inClassFieldInit;
  return (64 & t2) > 0 || s2 || this.options.allowSuperOutsideMethod;
}, Nh.allowDirectSuper.get = function() {
  return (128 & this.currentThisScope().flags) > 0;
}, Nh.treatFunctionsAsVar.get = function() {
  return this.treatFunctionsAsVarInScope(this.currentScope());
}, Nh.allowNewDotTarget.get = function() {
  var e3 = this.currentThisScope(), t2 = e3.flags, s2 = e3.inClassFieldInit;
  return (258 & t2) > 0 || s2;
}, Nh.inClassStaticBlock.get = function() {
  return (this.currentVarScope().flags & Ph) > 0;
}, $h.extend = function() {
  for (var e3 = [], t2 = arguments.length; t2--; )
    e3[t2] = arguments[t2];
  for (var s2 = this, i2 = 0; i2 < e3.length; i2++)
    s2 = e3[i2](s2);
  return s2;
}, $h.parse = function(e3, t2) {
  return new this(t2, e3).parse();
}, $h.parseExpressionAt = function(e3, t2, s2) {
  var i2 = new this(s2, e3, t2);
  return i2.nextToken(), i2.parseExpression();
}, $h.tokenizer = function(e3, t2) {
  return new this(t2, e3);
}, Object.defineProperties($h.prototype, Nh);
var _h = $h.prototype, Rh = /^(?:'((?:\\.|[^'\\])*?)'|"((?:\\.|[^"\\])*?)")/;
_h.strictDirective = function(e3) {
  if (this.options.ecmaVersion < 5)
    return false;
  for (; ; ) {
    dh.lastIndex = e3, e3 += dh.exec(this.input)[0].length;
    var t2 = Rh.exec(this.input.slice(e3));
    if (!t2)
      return false;
    if ("use strict" === (t2[1] || t2[2])) {
      dh.lastIndex = e3 + t2[0].length;
      var s2 = dh.exec(this.input), i2 = s2.index + s2[0].length, n2 = this.input.charAt(i2);
      return ";" === n2 || "}" === n2 || ah.test(s2[0]) && !(/[(`.[+\-/*%<>=,?^&]/.test(n2) || "!" === n2 && "=" === this.input.charAt(i2 + 1));
    }
    e3 += t2[0].length, dh.lastIndex = e3, e3 += dh.exec(this.input)[0].length, ";" === this.input[e3] && e3++;
  }
}, _h.eat = function(e3) {
  return this.type === e3 && (this.next(), true);
}, _h.isContextual = function(e3) {
  return this.type === oh.name && this.value === e3 && !this.containsEsc;
}, _h.eatContextual = function(e3) {
  return !!this.isContextual(e3) && (this.next(), true);
}, _h.expectContextual = function(e3) {
  this.eatContextual(e3) || this.unexpected();
}, _h.canInsertSemicolon = function() {
  return this.type === oh.eof || this.type === oh.braceR || ah.test(this.input.slice(this.lastTokEnd, this.start));
}, _h.insertSemicolon = function() {
  if (this.canInsertSemicolon())
    return this.options.onInsertedSemicolon && this.options.onInsertedSemicolon(this.lastTokEnd, this.lastTokEndLoc), true;
}, _h.semicolon = function() {
  this.eat(oh.semi) || this.insertSemicolon() || this.unexpected();
}, _h.afterTrailingComma = function(e3, t2) {
  if (this.type === e3)
    return this.options.onTrailingComma && this.options.onTrailingComma(this.lastTokStart, this.lastTokStartLoc), t2 || this.next(), true;
}, _h.expect = function(e3) {
  this.eat(e3) || this.unexpected();
}, _h.unexpected = function(e3) {
  this.raise(null != e3 ? e3 : this.start, "Unexpected token");
};
var Oh = function() {
  this.shorthandAssign = this.trailingComma = this.parenthesizedAssign = this.parenthesizedBind = this.doubleProto = -1;
};
_h.checkPatternErrors = function(e3, t2) {
  if (e3) {
    e3.trailingComma > -1 && this.raiseRecoverable(e3.trailingComma, "Comma is not permitted after the rest element");
    var s2 = t2 ? e3.parenthesizedAssign : e3.parenthesizedBind;
    s2 > -1 && this.raiseRecoverable(s2, t2 ? "Assigning to rvalue" : "Parenthesized pattern");
  }
}, _h.checkExpressionErrors = function(e3, t2) {
  if (!e3)
    return false;
  var s2 = e3.shorthandAssign, i2 = e3.doubleProto;
  if (!t2)
    return s2 >= 0 || i2 >= 0;
  s2 >= 0 && this.raise(s2, "Shorthand property assignments are valid only in destructuring patterns"), i2 >= 0 && this.raiseRecoverable(i2, "Redefinition of __proto__ property");
}, _h.checkYieldAwaitInDefaultParams = function() {
  this.yieldPos && (!this.awaitPos || this.yieldPos < this.awaitPos) && this.raise(this.yieldPos, "Yield expression cannot be a default value"), this.awaitPos && this.raise(this.awaitPos, "Await expression cannot be a default value");
}, _h.isSimpleAssignTarget = function(e3) {
  return "ParenthesizedExpression" === e3.type ? this.isSimpleAssignTarget(e3.expression) : "Identifier" === e3.type || "MemberExpression" === e3.type;
};
var Dh = $h.prototype;
Dh.parseTopLevel = function(e3) {
  var t2 = /* @__PURE__ */ Object.create(null);
  for (e3.body || (e3.body = []); this.type !== oh.eof; ) {
    var s2 = this.parseStatement(null, true, t2);
    e3.body.push(s2);
  }
  if (this.inModule)
    for (var i2 = 0, n2 = Object.keys(this.undefinedExports); i2 < n2.length; i2 += 1) {
      var r2 = n2[i2];
      this.raiseRecoverable(this.undefinedExports[r2].start, "Export '" + r2 + "' is not defined");
    }
  return this.adaptDirectivePrologue(e3.body), this.next(), e3.sourceType = this.options.sourceType, this.finishNode(e3, "Program");
};
var Lh = { kind: "loop" }, Th = { kind: "switch" };
Dh.isLet = function(e3) {
  if (this.options.ecmaVersion < 6 || !this.isContextual("let"))
    return false;
  dh.lastIndex = this.pos;
  var t2 = dh.exec(this.input), s2 = this.pos + t2[0].length, i2 = this.input.charCodeAt(s2);
  if (91 === i2 || 92 === i2)
    return true;
  if (e3)
    return false;
  if (123 === i2 || i2 > 55295 && i2 < 56320)
    return true;
  if (Zl(i2, true)) {
    for (var n2 = s2 + 1; Jl(i2 = this.input.charCodeAt(n2), true); )
      ++n2;
    if (92 === i2 || i2 > 55295 && i2 < 56320)
      return true;
    var r2 = this.input.slice(s2, n2);
    if (!Kl.test(r2))
      return true;
  }
  return false;
}, Dh.isAsyncFunction = function() {
  if (this.options.ecmaVersion < 8 || !this.isContextual("async"))
    return false;
  dh.lastIndex = this.pos;
  var e3, t2 = dh.exec(this.input), s2 = this.pos + t2[0].length;
  return !(ah.test(this.input.slice(this.pos, s2)) || "function" !== this.input.slice(s2, s2 + 8) || s2 + 8 !== this.input.length && (Jl(e3 = this.input.charCodeAt(s2 + 8)) || e3 > 55295 && e3 < 56320));
}, Dh.parseStatement = function(e3, t2, s2) {
  var i2, n2 = this.type, r2 = this.startNode();
  switch (this.isLet(e3) && (n2 = oh._var, i2 = "let"), n2) {
    case oh._break:
    case oh._continue:
      return this.parseBreakContinueStatement(r2, n2.keyword);
    case oh._debugger:
      return this.parseDebuggerStatement(r2);
    case oh._do:
      return this.parseDoStatement(r2);
    case oh._for:
      return this.parseForStatement(r2);
    case oh._function:
      return e3 && (this.strict || "if" !== e3 && "label" !== e3) && this.options.ecmaVersion >= 6 && this.unexpected(), this.parseFunctionStatement(r2, false, !e3);
    case oh._class:
      return e3 && this.unexpected(), this.parseClass(r2, true);
    case oh._if:
      return this.parseIfStatement(r2);
    case oh._return:
      return this.parseReturnStatement(r2);
    case oh._switch:
      return this.parseSwitchStatement(r2);
    case oh._throw:
      return this.parseThrowStatement(r2);
    case oh._try:
      return this.parseTryStatement(r2);
    case oh._const:
    case oh._var:
      return i2 = i2 || this.value, e3 && "var" !== i2 && this.unexpected(), this.parseVarStatement(r2, i2);
    case oh._while:
      return this.parseWhileStatement(r2);
    case oh._with:
      return this.parseWithStatement(r2);
    case oh.braceL:
      return this.parseBlock(true, r2);
    case oh.semi:
      return this.parseEmptyStatement(r2);
    case oh._export:
    case oh._import:
      if (this.options.ecmaVersion > 10 && n2 === oh._import) {
        dh.lastIndex = this.pos;
        var o2 = dh.exec(this.input), a2 = this.pos + o2[0].length, l2 = this.input.charCodeAt(a2);
        if (40 === l2 || 46 === l2)
          return this.parseExpressionStatement(r2, this.parseExpression());
      }
      return this.options.allowImportExportEverywhere || (t2 || this.raise(this.start, "'import' and 'export' may only appear at the top level"), this.inModule || this.raise(this.start, "'import' and 'export' may appear only with 'sourceType: module'")), n2 === oh._import ? this.parseImport(r2) : this.parseExport(r2, s2);
    default:
      if (this.isAsyncFunction())
        return e3 && this.unexpected(), this.next(), this.parseFunctionStatement(r2, true, !e3);
      var h2 = this.value, c2 = this.parseExpression();
      return n2 === oh.name && "Identifier" === c2.type && this.eat(oh.colon) ? this.parseLabeledStatement(r2, h2, c2, e3) : this.parseExpressionStatement(r2, c2);
  }
}, Dh.parseBreakContinueStatement = function(e3, t2) {
  var s2 = "break" === t2;
  this.next(), this.eat(oh.semi) || this.insertSemicolon() ? e3.label = null : this.type !== oh.name ? this.unexpected() : (e3.label = this.parseIdent(), this.semicolon());
  for (var i2 = 0; i2 < this.labels.length; ++i2) {
    var n2 = this.labels[i2];
    if (null == e3.label || n2.name === e3.label.name) {
      if (null != n2.kind && (s2 || "loop" === n2.kind))
        break;
      if (e3.label && s2)
        break;
    }
  }
  return i2 === this.labels.length && this.raise(e3.start, "Unsyntactic " + t2), this.finishNode(e3, s2 ? "BreakStatement" : "ContinueStatement");
}, Dh.parseDebuggerStatement = function(e3) {
  return this.next(), this.semicolon(), this.finishNode(e3, "DebuggerStatement");
}, Dh.parseDoStatement = function(e3) {
  return this.next(), this.labels.push(Lh), e3.body = this.parseStatement("do"), this.labels.pop(), this.expect(oh._while), e3.test = this.parseParenExpression(), this.options.ecmaVersion >= 6 ? this.eat(oh.semi) : this.semicolon(), this.finishNode(e3, "DoWhileStatement");
}, Dh.parseForStatement = function(e3) {
  this.next();
  var t2 = this.options.ecmaVersion >= 9 && this.canAwait && this.eatContextual("await") ? this.lastTokStart : -1;
  if (this.labels.push(Lh), this.enterScope(0), this.expect(oh.parenL), this.type === oh.semi)
    return t2 > -1 && this.unexpected(t2), this.parseFor(e3, null);
  var s2 = this.isLet();
  if (this.type === oh._var || this.type === oh._const || s2) {
    var i2 = this.startNode(), n2 = s2 ? "let" : this.value;
    return this.next(), this.parseVar(i2, true, n2), this.finishNode(i2, "VariableDeclaration"), (this.type === oh._in || this.options.ecmaVersion >= 6 && this.isContextual("of")) && 1 === i2.declarations.length ? (this.options.ecmaVersion >= 9 && (this.type === oh._in ? t2 > -1 && this.unexpected(t2) : e3.await = t2 > -1), this.parseForIn(e3, i2)) : (t2 > -1 && this.unexpected(t2), this.parseFor(e3, i2));
  }
  var r2 = this.isContextual("let"), o2 = false, a2 = new Oh(), l2 = this.parseExpression(!(t2 > -1) || "await", a2);
  return this.type === oh._in || (o2 = this.options.ecmaVersion >= 6 && this.isContextual("of")) ? (this.options.ecmaVersion >= 9 && (this.type === oh._in ? t2 > -1 && this.unexpected(t2) : e3.await = t2 > -1), r2 && o2 && this.raise(l2.start, "The left-hand side of a for-of loop may not start with 'let'."), this.toAssignable(l2, false, a2), this.checkLValPattern(l2), this.parseForIn(e3, l2)) : (this.checkExpressionErrors(a2, true), t2 > -1 && this.unexpected(t2), this.parseFor(e3, l2));
}, Dh.parseFunctionStatement = function(e3, t2, s2) {
  return this.next(), this.parseFunction(e3, Vh | (s2 ? 0 : Bh), false, t2);
}, Dh.parseIfStatement = function(e3) {
  return this.next(), e3.test = this.parseParenExpression(), e3.consequent = this.parseStatement("if"), e3.alternate = this.eat(oh._else) ? this.parseStatement("if") : null, this.finishNode(e3, "IfStatement");
}, Dh.parseReturnStatement = function(e3) {
  return this.inFunction || this.options.allowReturnOutsideFunction || this.raise(this.start, "'return' outside of function"), this.next(), this.eat(oh.semi) || this.insertSemicolon() ? e3.argument = null : (e3.argument = this.parseExpression(), this.semicolon()), this.finishNode(e3, "ReturnStatement");
}, Dh.parseSwitchStatement = function(e3) {
  var t2;
  this.next(), e3.discriminant = this.parseParenExpression(), e3.cases = [], this.expect(oh.braceL), this.labels.push(Th), this.enterScope(0);
  for (var s2 = false; this.type !== oh.braceR; )
    if (this.type === oh._case || this.type === oh._default) {
      var i2 = this.type === oh._case;
      t2 && this.finishNode(t2, "SwitchCase"), e3.cases.push(t2 = this.startNode()), t2.consequent = [], this.next(), i2 ? t2.test = this.parseExpression() : (s2 && this.raiseRecoverable(this.lastTokStart, "Multiple default clauses"), s2 = true, t2.test = null), this.expect(oh.colon);
    } else
      t2 || this.unexpected(), t2.consequent.push(this.parseStatement(null));
  return this.exitScope(), t2 && this.finishNode(t2, "SwitchCase"), this.next(), this.labels.pop(), this.finishNode(e3, "SwitchStatement");
}, Dh.parseThrowStatement = function(e3) {
  return this.next(), ah.test(this.input.slice(this.lastTokEnd, this.start)) && this.raise(this.lastTokEnd, "Illegal newline after throw"), e3.argument = this.parseExpression(), this.semicolon(), this.finishNode(e3, "ThrowStatement");
};
var Mh = [];
Dh.parseCatchClauseParam = function() {
  var e3 = this.parseBindingAtom(), t2 = "Identifier" === e3.type;
  return this.enterScope(t2 ? 32 : 0), this.checkLValPattern(e3, t2 ? 4 : 2), this.expect(oh.parenR), e3;
}, Dh.parseTryStatement = function(e3) {
  if (this.next(), e3.block = this.parseBlock(), e3.handler = null, this.type === oh._catch) {
    var t2 = this.startNode();
    this.next(), this.eat(oh.parenL) ? t2.param = this.parseCatchClauseParam() : (this.options.ecmaVersion < 10 && this.unexpected(), t2.param = null, this.enterScope(0)), t2.body = this.parseBlock(false), this.exitScope(), e3.handler = this.finishNode(t2, "CatchClause");
  }
  return e3.finalizer = this.eat(oh._finally) ? this.parseBlock() : null, e3.handler || e3.finalizer || this.raise(e3.start, "Missing catch or finally clause"), this.finishNode(e3, "TryStatement");
}, Dh.parseVarStatement = function(e3, t2, s2) {
  return this.next(), this.parseVar(e3, false, t2, s2), this.semicolon(), this.finishNode(e3, "VariableDeclaration");
}, Dh.parseWhileStatement = function(e3) {
  return this.next(), e3.test = this.parseParenExpression(), this.labels.push(Lh), e3.body = this.parseStatement("while"), this.labels.pop(), this.finishNode(e3, "WhileStatement");
}, Dh.parseWithStatement = function(e3) {
  return this.strict && this.raise(this.start, "'with' in strict mode"), this.next(), e3.object = this.parseParenExpression(), e3.body = this.parseStatement("with"), this.finishNode(e3, "WithStatement");
}, Dh.parseEmptyStatement = function(e3) {
  return this.next(), this.finishNode(e3, "EmptyStatement");
}, Dh.parseLabeledStatement = function(e3, t2, s2, i2) {
  for (var n2 = 0, r2 = this.labels; n2 < r2.length; n2 += 1) {
    r2[n2].name === t2 && this.raise(s2.start, "Label '" + t2 + "' is already declared");
  }
  for (var o2 = this.type.isLoop ? "loop" : this.type === oh._switch ? "switch" : null, a2 = this.labels.length - 1; a2 >= 0; a2--) {
    var l2 = this.labels[a2];
    if (l2.statementStart !== e3.start)
      break;
    l2.statementStart = this.start, l2.kind = o2;
  }
  return this.labels.push({ name: t2, kind: o2, statementStart: this.start }), e3.body = this.parseStatement(i2 ? -1 === i2.indexOf("label") ? i2 + "label" : i2 : "label"), this.labels.pop(), e3.label = s2, this.finishNode(e3, "LabeledStatement");
}, Dh.parseExpressionStatement = function(e3, t2) {
  return e3.expression = t2, this.semicolon(), this.finishNode(e3, "ExpressionStatement");
}, Dh.parseBlock = function(e3, t2, s2) {
  for (void 0 === e3 && (e3 = true), void 0 === t2 && (t2 = this.startNode()), t2.body = [], this.expect(oh.braceL), e3 && this.enterScope(0); this.type !== oh.braceR; ) {
    var i2 = this.parseStatement(null);
    t2.body.push(i2);
  }
  return s2 && (this.strict = false), this.next(), e3 && this.exitScope(), this.finishNode(t2, "BlockStatement");
}, Dh.parseFor = function(e3, t2) {
  return e3.init = t2, this.expect(oh.semi), e3.test = this.type === oh.semi ? null : this.parseExpression(), this.expect(oh.semi), e3.update = this.type === oh.parenR ? null : this.parseExpression(), this.expect(oh.parenR), e3.body = this.parseStatement("for"), this.exitScope(), this.labels.pop(), this.finishNode(e3, "ForStatement");
}, Dh.parseForIn = function(e3, t2) {
  var s2 = this.type === oh._in;
  return this.next(), "VariableDeclaration" === t2.type && null != t2.declarations[0].init && (!s2 || this.options.ecmaVersion < 8 || this.strict || "var" !== t2.kind || "Identifier" !== t2.declarations[0].id.type) && this.raise(t2.start, (s2 ? "for-in" : "for-of") + " loop variable declaration may not have an initializer"), e3.left = t2, e3.right = s2 ? this.parseExpression() : this.parseMaybeAssign(), this.expect(oh.parenR), e3.body = this.parseStatement("for"), this.exitScope(), this.labels.pop(), this.finishNode(e3, s2 ? "ForInStatement" : "ForOfStatement");
}, Dh.parseVar = function(e3, t2, s2, i2) {
  for (e3.declarations = [], e3.kind = s2; ; ) {
    var n2 = this.startNode();
    if (this.parseVarId(n2, s2), this.eat(oh.eq) ? n2.init = this.parseMaybeAssign(t2) : i2 || "const" !== s2 || this.type === oh._in || this.options.ecmaVersion >= 6 && this.isContextual("of") ? i2 || "Identifier" === n2.id.type || t2 && (this.type === oh._in || this.isContextual("of")) ? n2.init = null : this.raise(this.lastTokEnd, "Complex binding patterns require an initialization value") : this.unexpected(), e3.declarations.push(this.finishNode(n2, "VariableDeclarator")), !this.eat(oh.comma))
      break;
  }
  return e3;
}, Dh.parseVarId = function(e3, t2) {
  e3.id = this.parseBindingAtom(), this.checkLValPattern(e3.id, "var" === t2 ? 1 : 2, false);
};
var Vh = 1, Bh = 2;
function zh(e3, t2) {
  var s2 = t2.key.name, i2 = e3[s2], n2 = "true";
  return "MethodDefinition" !== t2.type || "get" !== t2.kind && "set" !== t2.kind || (n2 = (t2.static ? "s" : "i") + t2.kind), "iget" === i2 && "iset" === n2 || "iset" === i2 && "iget" === n2 || "sget" === i2 && "sset" === n2 || "sset" === i2 && "sget" === n2 ? (e3[s2] = "true", false) : !!i2 || (e3[s2] = n2, false);
}
function Fh(e3, t2) {
  var s2 = e3.computed, i2 = e3.key;
  return !s2 && ("Identifier" === i2.type && i2.name === t2 || "Literal" === i2.type && i2.value === t2);
}
Dh.parseFunction = function(e3, t2, s2, i2, n2) {
  this.initFunction(e3), (this.options.ecmaVersion >= 9 || this.options.ecmaVersion >= 6 && !i2) && (this.type === oh.star && t2 & Bh && this.unexpected(), e3.generator = this.eat(oh.star)), this.options.ecmaVersion >= 8 && (e3.async = !!i2), t2 & Vh && (e3.id = 4 & t2 && this.type !== oh.name ? null : this.parseIdent(), !e3.id || t2 & Bh || this.checkLValSimple(e3.id, this.strict || e3.generator || e3.async ? this.treatFunctionsAsVar ? 1 : 2 : 3));
  var r2 = this.yieldPos, o2 = this.awaitPos, a2 = this.awaitIdentPos;
  return this.yieldPos = 0, this.awaitPos = 0, this.awaitIdentPos = 0, this.enterScope(Ch(e3.async, e3.generator)), t2 & Vh || (e3.id = this.type === oh.name ? this.parseIdent() : null), this.parseFunctionParams(e3), this.parseFunctionBody(e3, s2, false, n2), this.yieldPos = r2, this.awaitPos = o2, this.awaitIdentPos = a2, this.finishNode(e3, t2 & Vh ? "FunctionDeclaration" : "FunctionExpression");
}, Dh.parseFunctionParams = function(e3) {
  this.expect(oh.parenL), e3.params = this.parseBindingList(oh.parenR, false, this.options.ecmaVersion >= 8), this.checkYieldAwaitInDefaultParams();
}, Dh.parseClass = function(e3, t2) {
  this.next();
  var s2 = this.strict;
  this.strict = true, this.parseClassId(e3, t2), this.parseClassSuper(e3);
  var i2 = this.enterClassBody(), n2 = this.startNode(), r2 = false;
  for (n2.body = [], this.expect(oh.braceL); this.type !== oh.braceR; ) {
    var o2 = this.parseClassElement(null !== e3.superClass);
    o2 && (n2.body.push(o2), "MethodDefinition" === o2.type && "constructor" === o2.kind ? (r2 && this.raiseRecoverable(o2.start, "Duplicate constructor in the same class"), r2 = true) : o2.key && "PrivateIdentifier" === o2.key.type && zh(i2, o2) && this.raiseRecoverable(o2.key.start, "Identifier '#" + o2.key.name + "' has already been declared"));
  }
  return this.strict = s2, this.next(), e3.body = this.finishNode(n2, "ClassBody"), this.exitClassBody(), this.finishNode(e3, t2 ? "ClassDeclaration" : "ClassExpression");
}, Dh.parseClassElement = function(e3) {
  if (this.eat(oh.semi))
    return null;
  var t2 = this.options.ecmaVersion, s2 = this.startNode(), i2 = "", n2 = false, r2 = false, o2 = "method", a2 = false;
  if (this.eatContextual("static")) {
    if (t2 >= 13 && this.eat(oh.braceL))
      return this.parseClassStaticBlock(s2), s2;
    this.isClassElementNameStart() || this.type === oh.star ? a2 = true : i2 = "static";
  }
  if (s2.static = a2, !i2 && t2 >= 8 && this.eatContextual("async") && (!this.isClassElementNameStart() && this.type !== oh.star || this.canInsertSemicolon() ? i2 = "async" : r2 = true), !i2 && (t2 >= 9 || !r2) && this.eat(oh.star) && (n2 = true), !i2 && !r2 && !n2) {
    var l2 = this.value;
    (this.eatContextual("get") || this.eatContextual("set")) && (this.isClassElementNameStart() ? o2 = l2 : i2 = l2);
  }
  if (i2 ? (s2.computed = false, s2.key = this.startNodeAt(this.lastTokStart, this.lastTokStartLoc), s2.key.name = i2, this.finishNode(s2.key, "Identifier")) : this.parseClassElementName(s2), t2 < 13 || this.type === oh.parenL || "method" !== o2 || n2 || r2) {
    var h2 = !s2.static && Fh(s2, "constructor"), c2 = h2 && e3;
    h2 && "method" !== o2 && this.raise(s2.key.start, "Constructor can't have get/set modifier"), s2.kind = h2 ? "constructor" : o2, this.parseClassMethod(s2, n2, r2, c2);
  } else
    this.parseClassField(s2);
  return s2;
}, Dh.isClassElementNameStart = function() {
  return this.type === oh.name || this.type === oh.privateId || this.type === oh.num || this.type === oh.string || this.type === oh.bracketL || this.type.keyword;
}, Dh.parseClassElementName = function(e3) {
  this.type === oh.privateId ? ("constructor" === this.value && this.raise(this.start, "Classes can't have an element named '#constructor'"), e3.computed = false, e3.key = this.parsePrivateIdent()) : this.parsePropertyName(e3);
}, Dh.parseClassMethod = function(e3, t2, s2, i2) {
  var n2 = e3.key;
  "constructor" === e3.kind ? (t2 && this.raise(n2.start, "Constructor can't be a generator"), s2 && this.raise(n2.start, "Constructor can't be an async method")) : e3.static && Fh(e3, "prototype") && this.raise(n2.start, "Classes may not have a static property named prototype");
  var r2 = e3.value = this.parseMethod(t2, s2, i2);
  return "get" === e3.kind && 0 !== r2.params.length && this.raiseRecoverable(r2.start, "getter should have no params"), "set" === e3.kind && 1 !== r2.params.length && this.raiseRecoverable(r2.start, "setter should have exactly one param"), "set" === e3.kind && "RestElement" === r2.params[0].type && this.raiseRecoverable(r2.params[0].start, "Setter cannot use rest params"), this.finishNode(e3, "MethodDefinition");
}, Dh.parseClassField = function(e3) {
  if (Fh(e3, "constructor") ? this.raise(e3.key.start, "Classes can't have a field named 'constructor'") : e3.static && Fh(e3, "prototype") && this.raise(e3.key.start, "Classes can't have a static field named 'prototype'"), this.eat(oh.eq)) {
    var t2 = this.currentThisScope(), s2 = t2.inClassFieldInit;
    t2.inClassFieldInit = true, e3.value = this.parseMaybeAssign(), t2.inClassFieldInit = s2;
  } else
    e3.value = null;
  return this.semicolon(), this.finishNode(e3, "PropertyDefinition");
}, Dh.parseClassStaticBlock = function(e3) {
  e3.body = [];
  var t2 = this.labels;
  for (this.labels = [], this.enterScope(320); this.type !== oh.braceR; ) {
    var s2 = this.parseStatement(null);
    e3.body.push(s2);
  }
  return this.next(), this.exitScope(), this.labels = t2, this.finishNode(e3, "StaticBlock");
}, Dh.parseClassId = function(e3, t2) {
  this.type === oh.name ? (e3.id = this.parseIdent(), t2 && this.checkLValSimple(e3.id, 2, false)) : (true === t2 && this.unexpected(), e3.id = null);
}, Dh.parseClassSuper = function(e3) {
  e3.superClass = this.eat(oh._extends) ? this.parseExprSubscripts(null, false) : null;
}, Dh.enterClassBody = function() {
  var e3 = { declared: /* @__PURE__ */ Object.create(null), used: [] };
  return this.privateNameStack.push(e3), e3.declared;
}, Dh.exitClassBody = function() {
  for (var e3 = this.privateNameStack.pop(), t2 = e3.declared, s2 = e3.used, i2 = this.privateNameStack.length, n2 = 0 === i2 ? null : this.privateNameStack[i2 - 1], r2 = 0; r2 < s2.length; ++r2) {
    var o2 = s2[r2];
    gh(t2, o2.name) || (n2 ? n2.used.push(o2) : this.raiseRecoverable(o2.start, "Private field '#" + o2.name + "' must be declared in an enclosing class"));
  }
}, Dh.parseExportAllDeclaration = function(e3, t2) {
  return this.options.ecmaVersion >= 11 && (this.eatContextual("as") ? (e3.exported = this.parseModuleExportName(), this.checkExport(t2, e3.exported, this.lastTokStart)) : e3.exported = null), this.expectContextual("from"), this.type !== oh.string && this.unexpected(), e3.source = this.parseExprAtom(), this.semicolon(), this.finishNode(e3, "ExportAllDeclaration");
}, Dh.parseExport = function(e3, t2) {
  if (this.next(), this.eat(oh.star))
    return this.parseExportAllDeclaration(e3, t2);
  if (this.eat(oh._default))
    return this.checkExport(t2, "default", this.lastTokStart), e3.declaration = this.parseExportDefaultDeclaration(), this.finishNode(e3, "ExportDefaultDeclaration");
  if (this.shouldParseExportStatement())
    e3.declaration = this.parseExportDeclaration(e3), "VariableDeclaration" === e3.declaration.type ? this.checkVariableExport(t2, e3.declaration.declarations) : this.checkExport(t2, e3.declaration.id, e3.declaration.id.start), e3.specifiers = [], e3.source = null;
  else {
    if (e3.declaration = null, e3.specifiers = this.parseExportSpecifiers(t2), this.eatContextual("from"))
      this.type !== oh.string && this.unexpected(), e3.source = this.parseExprAtom();
    else {
      for (var s2 = 0, i2 = e3.specifiers; s2 < i2.length; s2 += 1) {
        var n2 = i2[s2];
        this.checkUnreserved(n2.local), this.checkLocalExport(n2.local), "Literal" === n2.local.type && this.raise(n2.local.start, "A string literal cannot be used as an exported binding without `from`.");
      }
      e3.source = null;
    }
    this.semicolon();
  }
  return this.finishNode(e3, "ExportNamedDeclaration");
}, Dh.parseExportDeclaration = function(e3) {
  return this.parseStatement(null);
}, Dh.parseExportDefaultDeclaration = function() {
  var e3;
  if (this.type === oh._function || (e3 = this.isAsyncFunction())) {
    var t2 = this.startNode();
    return this.next(), e3 && this.next(), this.parseFunction(t2, 4 | Vh, false, e3);
  }
  if (this.type === oh._class) {
    var s2 = this.startNode();
    return this.parseClass(s2, "nullableID");
  }
  var i2 = this.parseMaybeAssign();
  return this.semicolon(), i2;
}, Dh.checkExport = function(e3, t2, s2) {
  e3 && ("string" != typeof t2 && (t2 = "Identifier" === t2.type ? t2.name : t2.value), gh(e3, t2) && this.raiseRecoverable(s2, "Duplicate export '" + t2 + "'"), e3[t2] = true);
}, Dh.checkPatternExport = function(e3, t2) {
  var s2 = t2.type;
  if ("Identifier" === s2)
    this.checkExport(e3, t2, t2.start);
  else if ("ObjectPattern" === s2)
    for (var i2 = 0, n2 = t2.properties; i2 < n2.length; i2 += 1) {
      var r2 = n2[i2];
      this.checkPatternExport(e3, r2);
    }
  else if ("ArrayPattern" === s2)
    for (var o2 = 0, a2 = t2.elements; o2 < a2.length; o2 += 1) {
      var l2 = a2[o2];
      l2 && this.checkPatternExport(e3, l2);
    }
  else
    "Property" === s2 ? this.checkPatternExport(e3, t2.value) : "AssignmentPattern" === s2 ? this.checkPatternExport(e3, t2.left) : "RestElement" === s2 ? this.checkPatternExport(e3, t2.argument) : "ParenthesizedExpression" === s2 && this.checkPatternExport(e3, t2.expression);
}, Dh.checkVariableExport = function(e3, t2) {
  if (e3)
    for (var s2 = 0, i2 = t2; s2 < i2.length; s2 += 1) {
      var n2 = i2[s2];
      this.checkPatternExport(e3, n2.id);
    }
}, Dh.shouldParseExportStatement = function() {
  return "var" === this.type.keyword || "const" === this.type.keyword || "class" === this.type.keyword || "function" === this.type.keyword || this.isLet() || this.isAsyncFunction();
}, Dh.parseExportSpecifier = function(e3) {
  var t2 = this.startNode();
  return t2.local = this.parseModuleExportName(), t2.exported = this.eatContextual("as") ? this.parseModuleExportName() : t2.local, this.checkExport(e3, t2.exported, t2.exported.start), this.finishNode(t2, "ExportSpecifier");
}, Dh.parseExportSpecifiers = function(e3) {
  var t2 = [], s2 = true;
  for (this.expect(oh.braceL); !this.eat(oh.braceR); ) {
    if (s2)
      s2 = false;
    else if (this.expect(oh.comma), this.afterTrailingComma(oh.braceR))
      break;
    t2.push(this.parseExportSpecifier(e3));
  }
  return t2;
}, Dh.parseImport = function(e3) {
  return this.next(), this.type === oh.string ? (e3.specifiers = Mh, e3.source = this.parseExprAtom()) : (e3.specifiers = this.parseImportSpecifiers(), this.expectContextual("from"), e3.source = this.type === oh.string ? this.parseExprAtom() : this.unexpected()), this.semicolon(), this.finishNode(e3, "ImportDeclaration");
}, Dh.parseImportSpecifier = function() {
  var e3 = this.startNode();
  return e3.imported = this.parseModuleExportName(), this.eatContextual("as") ? e3.local = this.parseIdent() : (this.checkUnreserved(e3.imported), e3.local = e3.imported), this.checkLValSimple(e3.local, 2), this.finishNode(e3, "ImportSpecifier");
}, Dh.parseImportDefaultSpecifier = function() {
  var e3 = this.startNode();
  return e3.local = this.parseIdent(), this.checkLValSimple(e3.local, 2), this.finishNode(e3, "ImportDefaultSpecifier");
}, Dh.parseImportNamespaceSpecifier = function() {
  var e3 = this.startNode();
  return this.next(), this.expectContextual("as"), e3.local = this.parseIdent(), this.checkLValSimple(e3.local, 2), this.finishNode(e3, "ImportNamespaceSpecifier");
}, Dh.parseImportSpecifiers = function() {
  var e3 = [], t2 = true;
  if (this.type === oh.name && (e3.push(this.parseImportDefaultSpecifier()), !this.eat(oh.comma)))
    return e3;
  if (this.type === oh.star)
    return e3.push(this.parseImportNamespaceSpecifier()), e3;
  for (this.expect(oh.braceL); !this.eat(oh.braceR); ) {
    if (t2)
      t2 = false;
    else if (this.expect(oh.comma), this.afterTrailingComma(oh.braceR))
      break;
    e3.push(this.parseImportSpecifier());
  }
  return e3;
}, Dh.parseModuleExportName = function() {
  if (this.options.ecmaVersion >= 13 && this.type === oh.string) {
    var e3 = this.parseLiteral(this.value);
    return bh.test(e3.value) && this.raise(e3.start, "An export name cannot include a lone surrogate."), e3;
  }
  return this.parseIdent(true);
}, Dh.adaptDirectivePrologue = function(e3) {
  for (var t2 = 0; t2 < e3.length && this.isDirectiveCandidate(e3[t2]); ++t2)
    e3[t2].directive = e3[t2].expression.raw.slice(1, -1);
}, Dh.isDirectiveCandidate = function(e3) {
  return this.options.ecmaVersion >= 5 && "ExpressionStatement" === e3.type && "Literal" === e3.expression.type && "string" == typeof e3.expression.value && ('"' === this.input[e3.start] || "'" === this.input[e3.start]);
};
var jh = $h.prototype;
jh.toAssignable = function(e3, t2, s2) {
  if (this.options.ecmaVersion >= 6 && e3)
    switch (e3.type) {
      case "Identifier":
        this.inAsync && "await" === e3.name && this.raise(e3.start, "Cannot use 'await' as identifier inside an async function");
        break;
      case "ObjectPattern":
      case "ArrayPattern":
      case "AssignmentPattern":
      case "RestElement":
        break;
      case "ObjectExpression":
        e3.type = "ObjectPattern", s2 && this.checkPatternErrors(s2, true);
        for (var i2 = 0, n2 = e3.properties; i2 < n2.length; i2 += 1) {
          var r2 = n2[i2];
          this.toAssignable(r2, t2), "RestElement" !== r2.type || "ArrayPattern" !== r2.argument.type && "ObjectPattern" !== r2.argument.type || this.raise(r2.argument.start, "Unexpected token");
        }
        break;
      case "Property":
        "init" !== e3.kind && this.raise(e3.key.start, "Object pattern can't contain getter or setter"), this.toAssignable(e3.value, t2);
        break;
      case "ArrayExpression":
        e3.type = "ArrayPattern", s2 && this.checkPatternErrors(s2, true), this.toAssignableList(e3.elements, t2);
        break;
      case "SpreadElement":
        e3.type = "RestElement", this.toAssignable(e3.argument, t2), "AssignmentPattern" === e3.argument.type && this.raise(e3.argument.start, "Rest elements cannot have a default value");
        break;
      case "AssignmentExpression":
        "=" !== e3.operator && this.raise(e3.left.end, "Only '=' operator can be used for specifying default value."), e3.type = "AssignmentPattern", delete e3.operator, this.toAssignable(e3.left, t2);
        break;
      case "ParenthesizedExpression":
        this.toAssignable(e3.expression, t2, s2);
        break;
      case "ChainExpression":
        this.raiseRecoverable(e3.start, "Optional chaining cannot appear in left-hand side");
        break;
      case "MemberExpression":
        if (!t2)
          break;
      default:
        this.raise(e3.start, "Assigning to rvalue");
    }
  else
    s2 && this.checkPatternErrors(s2, true);
  return e3;
}, jh.toAssignableList = function(e3, t2) {
  for (var s2 = e3.length, i2 = 0; i2 < s2; i2++) {
    var n2 = e3[i2];
    n2 && this.toAssignable(n2, t2);
  }
  if (s2) {
    var r2 = e3[s2 - 1];
    6 === this.options.ecmaVersion && t2 && r2 && "RestElement" === r2.type && "Identifier" !== r2.argument.type && this.unexpected(r2.argument.start);
  }
  return e3;
}, jh.parseSpread = function(e3) {
  var t2 = this.startNode();
  return this.next(), t2.argument = this.parseMaybeAssign(false, e3), this.finishNode(t2, "SpreadElement");
}, jh.parseRestBinding = function() {
  var e3 = this.startNode();
  return this.next(), 6 === this.options.ecmaVersion && this.type !== oh.name && this.unexpected(), e3.argument = this.parseBindingAtom(), this.finishNode(e3, "RestElement");
}, jh.parseBindingAtom = function() {
  if (this.options.ecmaVersion >= 6)
    switch (this.type) {
      case oh.bracketL:
        var e3 = this.startNode();
        return this.next(), e3.elements = this.parseBindingList(oh.bracketR, true, true), this.finishNode(e3, "ArrayPattern");
      case oh.braceL:
        return this.parseObj(true);
    }
  return this.parseIdent();
}, jh.parseBindingList = function(e3, t2, s2, i2) {
  for (var n2 = [], r2 = true; !this.eat(e3); )
    if (r2 ? r2 = false : this.expect(oh.comma), t2 && this.type === oh.comma)
      n2.push(null);
    else {
      if (s2 && this.afterTrailingComma(e3))
        break;
      if (this.type === oh.ellipsis) {
        var o2 = this.parseRestBinding();
        this.parseBindingListItem(o2), n2.push(o2), this.type === oh.comma && this.raiseRecoverable(this.start, "Comma is not permitted after the rest element"), this.expect(e3);
        break;
      }
      n2.push(this.parseAssignableListItem(i2));
    }
  return n2;
}, jh.parseAssignableListItem = function(e3) {
  var t2 = this.parseMaybeDefault(this.start, this.startLoc);
  return this.parseBindingListItem(t2), t2;
}, jh.parseBindingListItem = function(e3) {
  return e3;
}, jh.parseMaybeDefault = function(e3, t2, s2) {
  if (s2 = s2 || this.parseBindingAtom(), this.options.ecmaVersion < 6 || !this.eat(oh.eq))
    return s2;
  var i2 = this.startNodeAt(e3, t2);
  return i2.left = s2, i2.right = this.parseMaybeAssign(), this.finishNode(i2, "AssignmentPattern");
}, jh.checkLValSimple = function(e3, t2, s2) {
  void 0 === t2 && (t2 = 0);
  var i2 = 0 !== t2;
  switch (e3.type) {
    case "Identifier":
      this.strict && this.reservedWordsStrictBind.test(e3.name) && this.raiseRecoverable(e3.start, (i2 ? "Binding " : "Assigning to ") + e3.name + " in strict mode"), i2 && (2 === t2 && "let" === e3.name && this.raiseRecoverable(e3.start, "let is disallowed as a lexically bound name"), s2 && (gh(s2, e3.name) && this.raiseRecoverable(e3.start, "Argument name clash"), s2[e3.name] = true), 5 !== t2 && this.declareName(e3.name, t2, e3.start));
      break;
    case "ChainExpression":
      this.raiseRecoverable(e3.start, "Optional chaining cannot appear in left-hand side");
      break;
    case "MemberExpression":
      i2 && this.raiseRecoverable(e3.start, "Binding member expression");
      break;
    case "ParenthesizedExpression":
      return i2 && this.raiseRecoverable(e3.start, "Binding parenthesized expression"), this.checkLValSimple(e3.expression, t2, s2);
    default:
      this.raise(e3.start, (i2 ? "Binding" : "Assigning to") + " rvalue");
  }
}, jh.checkLValPattern = function(e3, t2, s2) {
  switch (void 0 === t2 && (t2 = 0), e3.type) {
    case "ObjectPattern":
      for (var i2 = 0, n2 = e3.properties; i2 < n2.length; i2 += 1) {
        var r2 = n2[i2];
        this.checkLValInnerPattern(r2, t2, s2);
      }
      break;
    case "ArrayPattern":
      for (var o2 = 0, a2 = e3.elements; o2 < a2.length; o2 += 1) {
        var l2 = a2[o2];
        l2 && this.checkLValInnerPattern(l2, t2, s2);
      }
      break;
    default:
      this.checkLValSimple(e3, t2, s2);
  }
}, jh.checkLValInnerPattern = function(e3, t2, s2) {
  switch (void 0 === t2 && (t2 = 0), e3.type) {
    case "Property":
      this.checkLValInnerPattern(e3.value, t2, s2);
      break;
    case "AssignmentPattern":
      this.checkLValPattern(e3.left, t2, s2);
      break;
    case "RestElement":
      this.checkLValPattern(e3.argument, t2, s2);
      break;
    default:
      this.checkLValPattern(e3, t2, s2);
  }
};
var Uh = function(e3, t2, s2, i2, n2) {
  this.token = e3, this.isExpr = !!t2, this.preserveSpace = !!s2, this.override = i2, this.generator = !!n2;
}, Gh = { b_stat: new Uh("{", false), b_expr: new Uh("{", true), b_tmpl: new Uh("${", false), p_stat: new Uh("(", false), p_expr: new Uh("(", true), q_tmpl: new Uh("`", true, true, function(e3) {
  return e3.tryReadTemplateToken();
}), f_stat: new Uh("function", false), f_expr: new Uh("function", true), f_expr_gen: new Uh("function", true, false, null, true), f_gen: new Uh("function", false, false, null, true) }, Wh = $h.prototype;
Wh.initialContext = function() {
  return [Gh.b_stat];
}, Wh.curContext = function() {
  return this.context[this.context.length - 1];
}, Wh.braceIsBlock = function(e3) {
  var t2 = this.curContext();
  return t2 === Gh.f_expr || t2 === Gh.f_stat || (e3 !== oh.colon || t2 !== Gh.b_stat && t2 !== Gh.b_expr ? e3 === oh._return || e3 === oh.name && this.exprAllowed ? ah.test(this.input.slice(this.lastTokEnd, this.start)) : e3 === oh._else || e3 === oh.semi || e3 === oh.eof || e3 === oh.parenR || e3 === oh.arrow || (e3 === oh.braceL ? t2 === Gh.b_stat : e3 !== oh._var && e3 !== oh._const && e3 !== oh.name && !this.exprAllowed) : !t2.isExpr);
}, Wh.inGeneratorContext = function() {
  for (var e3 = this.context.length - 1; e3 >= 1; e3--) {
    var t2 = this.context[e3];
    if ("function" === t2.token)
      return t2.generator;
  }
  return false;
}, Wh.updateContext = function(e3) {
  var t2, s2 = this.type;
  s2.keyword && e3 === oh.dot ? this.exprAllowed = false : (t2 = s2.updateContext) ? t2.call(this, e3) : this.exprAllowed = s2.beforeExpr;
}, Wh.overrideContext = function(e3) {
  this.curContext() !== e3 && (this.context[this.context.length - 1] = e3);
}, oh.parenR.updateContext = oh.braceR.updateContext = function() {
  if (1 !== this.context.length) {
    var e3 = this.context.pop();
    e3 === Gh.b_stat && "function" === this.curContext().token && (e3 = this.context.pop()), this.exprAllowed = !e3.isExpr;
  } else
    this.exprAllowed = true;
}, oh.braceL.updateContext = function(e3) {
  this.context.push(this.braceIsBlock(e3) ? Gh.b_stat : Gh.b_expr), this.exprAllowed = true;
}, oh.dollarBraceL.updateContext = function() {
  this.context.push(Gh.b_tmpl), this.exprAllowed = true;
}, oh.parenL.updateContext = function(e3) {
  var t2 = e3 === oh._if || e3 === oh._for || e3 === oh._with || e3 === oh._while;
  this.context.push(t2 ? Gh.p_stat : Gh.p_expr), this.exprAllowed = true;
}, oh.incDec.updateContext = function() {
}, oh._function.updateContext = oh._class.updateContext = function(e3) {
  !e3.beforeExpr || e3 === oh._else || e3 === oh.semi && this.curContext() !== Gh.p_stat || e3 === oh._return && ah.test(this.input.slice(this.lastTokEnd, this.start)) || (e3 === oh.colon || e3 === oh.braceL) && this.curContext() === Gh.b_stat ? this.context.push(Gh.f_stat) : this.context.push(Gh.f_expr), this.exprAllowed = false;
}, oh.backQuote.updateContext = function() {
  this.curContext() === Gh.q_tmpl ? this.context.pop() : this.context.push(Gh.q_tmpl), this.exprAllowed = false;
}, oh.star.updateContext = function(e3) {
  if (e3 === oh._function) {
    var t2 = this.context.length - 1;
    this.context[t2] === Gh.f_expr ? this.context[t2] = Gh.f_expr_gen : this.context[t2] = Gh.f_gen;
  }
  this.exprAllowed = true;
}, oh.name.updateContext = function(e3) {
  var t2 = false;
  this.options.ecmaVersion >= 6 && e3 !== oh.dot && ("of" === this.value && !this.exprAllowed || "yield" === this.value && this.inGeneratorContext()) && (t2 = true), this.exprAllowed = t2;
};
var qh = $h.prototype;
function Hh(e3) {
  return "MemberExpression" === e3.type && "PrivateIdentifier" === e3.property.type || "ChainExpression" === e3.type && Hh(e3.expression);
}
qh.checkPropClash = function(e3, t2, s2) {
  if (!(this.options.ecmaVersion >= 9 && "SpreadElement" === e3.type || this.options.ecmaVersion >= 6 && (e3.computed || e3.method || e3.shorthand))) {
    var i2, n2 = e3.key;
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
    var r2 = e3.kind;
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
}, qh.parseExpression = function(e3, t2) {
  var s2 = this.start, i2 = this.startLoc, n2 = this.parseMaybeAssign(e3, t2);
  if (this.type === oh.comma) {
    var r2 = this.startNodeAt(s2, i2);
    for (r2.expressions = [n2]; this.eat(oh.comma); )
      r2.expressions.push(this.parseMaybeAssign(e3, t2));
    return this.finishNode(r2, "SequenceExpression");
  }
  return n2;
}, qh.parseMaybeAssign = function(e3, t2, s2) {
  if (this.isContextual("yield")) {
    if (this.inGenerator)
      return this.parseYield(e3);
    this.exprAllowed = false;
  }
  var i2 = false, n2 = -1, r2 = -1, o2 = -1;
  t2 ? (n2 = t2.parenthesizedAssign, r2 = t2.trailingComma, o2 = t2.doubleProto, t2.parenthesizedAssign = t2.trailingComma = -1) : (t2 = new Oh(), i2 = true);
  var a2 = this.start, l2 = this.startLoc;
  this.type !== oh.parenL && this.type !== oh.name || (this.potentialArrowAt = this.start, this.potentialArrowInForAwait = "await" === e3);
  var h2 = this.parseMaybeConditional(e3, t2);
  if (s2 && (h2 = s2.call(this, h2, a2, l2)), this.type.isAssign) {
    var c2 = this.startNodeAt(a2, l2);
    return c2.operator = this.value, this.type === oh.eq && (h2 = this.toAssignable(h2, false, t2)), i2 || (t2.parenthesizedAssign = t2.trailingComma = t2.doubleProto = -1), t2.shorthandAssign >= h2.start && (t2.shorthandAssign = -1), this.type === oh.eq ? this.checkLValPattern(h2) : this.checkLValSimple(h2), c2.left = h2, this.next(), c2.right = this.parseMaybeAssign(e3), o2 > -1 && (t2.doubleProto = o2), this.finishNode(c2, "AssignmentExpression");
  }
  return i2 && this.checkExpressionErrors(t2, true), n2 > -1 && (t2.parenthesizedAssign = n2), r2 > -1 && (t2.trailingComma = r2), h2;
}, qh.parseMaybeConditional = function(e3, t2) {
  var s2 = this.start, i2 = this.startLoc, n2 = this.parseExprOps(e3, t2);
  if (this.checkExpressionErrors(t2))
    return n2;
  if (this.eat(oh.question)) {
    var r2 = this.startNodeAt(s2, i2);
    return r2.test = n2, r2.consequent = this.parseMaybeAssign(), this.expect(oh.colon), r2.alternate = this.parseMaybeAssign(e3), this.finishNode(r2, "ConditionalExpression");
  }
  return n2;
}, qh.parseExprOps = function(e3, t2) {
  var s2 = this.start, i2 = this.startLoc, n2 = this.parseMaybeUnary(t2, false, false, e3);
  return this.checkExpressionErrors(t2) || n2.start === s2 && "ArrowFunctionExpression" === n2.type ? n2 : this.parseExprOp(n2, s2, i2, -1, e3);
}, qh.parseExprOp = function(e3, t2, s2, i2, n2) {
  var r2 = this.type.binop;
  if (null != r2 && (!n2 || this.type !== oh._in) && r2 > i2) {
    var o2 = this.type === oh.logicalOR || this.type === oh.logicalAND, a2 = this.type === oh.coalesce;
    a2 && (r2 = oh.logicalAND.binop);
    var l2 = this.value;
    this.next();
    var h2 = this.start, c2 = this.startLoc, u2 = this.parseExprOp(this.parseMaybeUnary(null, false, false, n2), h2, c2, r2, n2), d2 = this.buildBinary(t2, s2, e3, u2, l2, o2 || a2);
    return (o2 && this.type === oh.coalesce || a2 && (this.type === oh.logicalOR || this.type === oh.logicalAND)) && this.raiseRecoverable(this.start, "Logical expressions and coalesce expressions cannot be mixed. Wrap either by parentheses"), this.parseExprOp(d2, t2, s2, i2, n2);
  }
  return e3;
}, qh.buildBinary = function(e3, t2, s2, i2, n2, r2) {
  "PrivateIdentifier" === i2.type && this.raise(i2.start, "Private identifier can only be left side of binary expression");
  var o2 = this.startNodeAt(e3, t2);
  return o2.left = s2, o2.operator = n2, o2.right = i2, this.finishNode(o2, r2 ? "LogicalExpression" : "BinaryExpression");
}, qh.parseMaybeUnary = function(e3, t2, s2, i2) {
  var n2, r2 = this.start, o2 = this.startLoc;
  if (this.isContextual("await") && this.canAwait)
    n2 = this.parseAwait(i2), t2 = true;
  else if (this.type.prefix) {
    var a2 = this.startNode(), l2 = this.type === oh.incDec;
    a2.operator = this.value, a2.prefix = true, this.next(), a2.argument = this.parseMaybeUnary(null, true, l2, i2), this.checkExpressionErrors(e3, true), l2 ? this.checkLValSimple(a2.argument) : this.strict && "delete" === a2.operator && "Identifier" === a2.argument.type ? this.raiseRecoverable(a2.start, "Deleting local variable in strict mode") : "delete" === a2.operator && Hh(a2.argument) ? this.raiseRecoverable(a2.start, "Private fields can not be deleted") : t2 = true, n2 = this.finishNode(a2, l2 ? "UpdateExpression" : "UnaryExpression");
  } else if (t2 || this.type !== oh.privateId) {
    if (n2 = this.parseExprSubscripts(e3, i2), this.checkExpressionErrors(e3))
      return n2;
    for (; this.type.postfix && !this.canInsertSemicolon(); ) {
      var h2 = this.startNodeAt(r2, o2);
      h2.operator = this.value, h2.prefix = false, h2.argument = n2, this.checkLValSimple(n2), this.next(), n2 = this.finishNode(h2, "UpdateExpression");
    }
  } else
    (i2 || 0 === this.privateNameStack.length) && this.unexpected(), n2 = this.parsePrivateIdent(), this.type !== oh._in && this.unexpected();
  return s2 || !this.eat(oh.starstar) ? n2 : t2 ? void this.unexpected(this.lastTokStart) : this.buildBinary(r2, o2, n2, this.parseMaybeUnary(null, false, false, i2), "**", false);
}, qh.parseExprSubscripts = function(e3, t2) {
  var s2 = this.start, i2 = this.startLoc, n2 = this.parseExprAtom(e3, t2);
  if ("ArrowFunctionExpression" === n2.type && ")" !== this.input.slice(this.lastTokStart, this.lastTokEnd))
    return n2;
  var r2 = this.parseSubscripts(n2, s2, i2, false, t2);
  return e3 && "MemberExpression" === r2.type && (e3.parenthesizedAssign >= r2.start && (e3.parenthesizedAssign = -1), e3.parenthesizedBind >= r2.start && (e3.parenthesizedBind = -1), e3.trailingComma >= r2.start && (e3.trailingComma = -1)), r2;
}, qh.parseSubscripts = function(e3, t2, s2, i2, n2) {
  for (var r2 = this.options.ecmaVersion >= 8 && "Identifier" === e3.type && "async" === e3.name && this.lastTokEnd === e3.end && !this.canInsertSemicolon() && e3.end - e3.start == 5 && this.potentialArrowAt === e3.start, o2 = false; ; ) {
    var a2 = this.parseSubscript(e3, t2, s2, i2, r2, o2, n2);
    if (a2.optional && (o2 = true), a2 === e3 || "ArrowFunctionExpression" === a2.type) {
      if (o2) {
        var l2 = this.startNodeAt(t2, s2);
        l2.expression = a2, a2 = this.finishNode(l2, "ChainExpression");
      }
      return a2;
    }
    e3 = a2;
  }
}, qh.shouldParseAsyncArrow = function() {
  return !this.canInsertSemicolon() && this.eat(oh.arrow);
}, qh.parseSubscriptAsyncArrow = function(e3, t2, s2, i2) {
  return this.parseArrowExpression(this.startNodeAt(e3, t2), s2, true, i2);
}, qh.parseSubscript = function(e3, t2, s2, i2, n2, r2, o2) {
  var a2 = this.options.ecmaVersion >= 11, l2 = a2 && this.eat(oh.questionDot);
  i2 && l2 && this.raise(this.lastTokStart, "Optional chaining cannot appear in the callee of new expressions");
  var h2 = this.eat(oh.bracketL);
  if (h2 || l2 && this.type !== oh.parenL && this.type !== oh.backQuote || this.eat(oh.dot)) {
    var c2 = this.startNodeAt(t2, s2);
    c2.object = e3, h2 ? (c2.property = this.parseExpression(), this.expect(oh.bracketR)) : this.type === oh.privateId && "Super" !== e3.type ? c2.property = this.parsePrivateIdent() : c2.property = this.parseIdent("never" !== this.options.allowReserved), c2.computed = !!h2, a2 && (c2.optional = l2), e3 = this.finishNode(c2, "MemberExpression");
  } else if (!i2 && this.eat(oh.parenL)) {
    var u2 = new Oh(), d2 = this.yieldPos, p2 = this.awaitPos, f2 = this.awaitIdentPos;
    this.yieldPos = 0, this.awaitPos = 0, this.awaitIdentPos = 0;
    var m2 = this.parseExprList(oh.parenR, this.options.ecmaVersion >= 8, false, u2);
    if (n2 && !l2 && this.shouldParseAsyncArrow())
      return this.checkPatternErrors(u2, false), this.checkYieldAwaitInDefaultParams(), this.awaitIdentPos > 0 && this.raise(this.awaitIdentPos, "Cannot use 'await' as identifier inside an async function"), this.yieldPos = d2, this.awaitPos = p2, this.awaitIdentPos = f2, this.parseSubscriptAsyncArrow(t2, s2, m2, o2);
    this.checkExpressionErrors(u2, true), this.yieldPos = d2 || this.yieldPos, this.awaitPos = p2 || this.awaitPos, this.awaitIdentPos = f2 || this.awaitIdentPos;
    var g2 = this.startNodeAt(t2, s2);
    g2.callee = e3, g2.arguments = m2, a2 && (g2.optional = l2), e3 = this.finishNode(g2, "CallExpression");
  } else if (this.type === oh.backQuote) {
    (l2 || r2) && this.raise(this.start, "Optional chaining cannot appear in the tag of tagged template expressions");
    var y2 = this.startNodeAt(t2, s2);
    y2.tag = e3, y2.quasi = this.parseTemplate({ isTagged: true }), e3 = this.finishNode(y2, "TaggedTemplateExpression");
  }
  return e3;
}, qh.parseExprAtom = function(e3, t2, s2) {
  this.type === oh.slash && this.readRegexp();
  var i2, n2 = this.potentialArrowAt === this.start;
  switch (this.type) {
    case oh._super:
      return this.allowSuper || this.raise(this.start, "'super' keyword outside a method"), i2 = this.startNode(), this.next(), this.type !== oh.parenL || this.allowDirectSuper || this.raise(i2.start, "super() call outside constructor of a subclass"), this.type !== oh.dot && this.type !== oh.bracketL && this.type !== oh.parenL && this.unexpected(), this.finishNode(i2, "Super");
    case oh._this:
      return i2 = this.startNode(), this.next(), this.finishNode(i2, "ThisExpression");
    case oh.name:
      var r2 = this.start, o2 = this.startLoc, a2 = this.containsEsc, l2 = this.parseIdent(false);
      if (this.options.ecmaVersion >= 8 && !a2 && "async" === l2.name && !this.canInsertSemicolon() && this.eat(oh._function))
        return this.overrideContext(Gh.f_expr), this.parseFunction(this.startNodeAt(r2, o2), 0, false, true, t2);
      if (n2 && !this.canInsertSemicolon()) {
        if (this.eat(oh.arrow))
          return this.parseArrowExpression(this.startNodeAt(r2, o2), [l2], false, t2);
        if (this.options.ecmaVersion >= 8 && "async" === l2.name && this.type === oh.name && !a2 && (!this.potentialArrowInForAwait || "of" !== this.value || this.containsEsc))
          return l2 = this.parseIdent(false), !this.canInsertSemicolon() && this.eat(oh.arrow) || this.unexpected(), this.parseArrowExpression(this.startNodeAt(r2, o2), [l2], true, t2);
      }
      return l2;
    case oh.regexp:
      var h2 = this.value;
      return (i2 = this.parseLiteral(h2.value)).regex = { pattern: h2.pattern, flags: h2.flags }, i2;
    case oh.num:
    case oh.string:
      return this.parseLiteral(this.value);
    case oh._null:
    case oh._true:
    case oh._false:
      return (i2 = this.startNode()).value = this.type === oh._null ? null : this.type === oh._true, i2.raw = this.type.keyword, this.next(), this.finishNode(i2, "Literal");
    case oh.parenL:
      var c2 = this.start, u2 = this.parseParenAndDistinguishExpression(n2, t2);
      return e3 && (e3.parenthesizedAssign < 0 && !this.isSimpleAssignTarget(u2) && (e3.parenthesizedAssign = c2), e3.parenthesizedBind < 0 && (e3.parenthesizedBind = c2)), u2;
    case oh.bracketL:
      return i2 = this.startNode(), this.next(), i2.elements = this.parseExprList(oh.bracketR, true, true, e3), this.finishNode(i2, "ArrayExpression");
    case oh.braceL:
      return this.overrideContext(Gh.b_expr), this.parseObj(false, e3);
    case oh._function:
      return i2 = this.startNode(), this.next(), this.parseFunction(i2, 0);
    case oh._class:
      return this.parseClass(this.startNode(), false);
    case oh._new:
      return this.parseNew();
    case oh.backQuote:
      return this.parseTemplate();
    case oh._import:
      return this.options.ecmaVersion >= 11 ? this.parseExprImport(s2) : this.unexpected();
    default:
      return this.parseExprAtomDefault();
  }
}, qh.parseExprAtomDefault = function() {
  this.unexpected();
}, qh.parseExprImport = function(e3) {
  var t2 = this.startNode();
  this.containsEsc && this.raiseRecoverable(this.start, "Escape sequence in keyword import");
  var s2 = this.parseIdent(true);
  return this.type !== oh.parenL || e3 ? this.type === oh.dot ? (t2.meta = s2, this.parseImportMeta(t2)) : void this.unexpected() : this.parseDynamicImport(t2);
}, qh.parseDynamicImport = function(e3) {
  if (this.next(), e3.source = this.parseMaybeAssign(), !this.eat(oh.parenR)) {
    var t2 = this.start;
    this.eat(oh.comma) && this.eat(oh.parenR) ? this.raiseRecoverable(t2, "Trailing comma is not allowed in import()") : this.unexpected(t2);
  }
  return this.finishNode(e3, "ImportExpression");
}, qh.parseImportMeta = function(e3) {
  this.next();
  var t2 = this.containsEsc;
  return e3.property = this.parseIdent(true), "meta" !== e3.property.name && this.raiseRecoverable(e3.property.start, "The only valid meta property for import is 'import.meta'"), t2 && this.raiseRecoverable(e3.start, "'import.meta' must not contain escaped characters"), "module" === this.options.sourceType || this.options.allowImportExportEverywhere || this.raiseRecoverable(e3.start, "Cannot use 'import.meta' outside a module"), this.finishNode(e3, "MetaProperty");
}, qh.parseLiteral = function(e3) {
  var t2 = this.startNode();
  return t2.value = e3, t2.raw = this.input.slice(this.start, this.end), 110 === t2.raw.charCodeAt(t2.raw.length - 1) && (t2.bigint = t2.raw.slice(0, -1).replace(/_/g, "")), this.next(), this.finishNode(t2, "Literal");
}, qh.parseParenExpression = function() {
  this.expect(oh.parenL);
  var e3 = this.parseExpression();
  return this.expect(oh.parenR), e3;
}, qh.shouldParseArrow = function(e3) {
  return !this.canInsertSemicolon();
}, qh.parseParenAndDistinguishExpression = function(e3, t2) {
  var s2, i2 = this.start, n2 = this.startLoc, r2 = this.options.ecmaVersion >= 8;
  if (this.options.ecmaVersion >= 6) {
    this.next();
    var o2, a2 = this.start, l2 = this.startLoc, h2 = [], c2 = true, u2 = false, d2 = new Oh(), p2 = this.yieldPos, f2 = this.awaitPos;
    for (this.yieldPos = 0, this.awaitPos = 0; this.type !== oh.parenR; ) {
      if (c2 ? c2 = false : this.expect(oh.comma), r2 && this.afterTrailingComma(oh.parenR, true)) {
        u2 = true;
        break;
      }
      if (this.type === oh.ellipsis) {
        o2 = this.start, h2.push(this.parseParenItem(this.parseRestBinding())), this.type === oh.comma && this.raiseRecoverable(this.start, "Comma is not permitted after the rest element");
        break;
      }
      h2.push(this.parseMaybeAssign(false, d2, this.parseParenItem));
    }
    var m2 = this.lastTokEnd, g2 = this.lastTokEndLoc;
    if (this.expect(oh.parenR), e3 && this.shouldParseArrow(h2) && this.eat(oh.arrow))
      return this.checkPatternErrors(d2, false), this.checkYieldAwaitInDefaultParams(), this.yieldPos = p2, this.awaitPos = f2, this.parseParenArrowList(i2, n2, h2, t2);
    h2.length && !u2 || this.unexpected(this.lastTokStart), o2 && this.unexpected(o2), this.checkExpressionErrors(d2, true), this.yieldPos = p2 || this.yieldPos, this.awaitPos = f2 || this.awaitPos, h2.length > 1 ? ((s2 = this.startNodeAt(a2, l2)).expressions = h2, this.finishNodeAt(s2, "SequenceExpression", m2, g2)) : s2 = h2[0];
  } else
    s2 = this.parseParenExpression();
  if (this.options.preserveParens) {
    var y2 = this.startNodeAt(i2, n2);
    return y2.expression = s2, this.finishNode(y2, "ParenthesizedExpression");
  }
  return s2;
}, qh.parseParenItem = function(e3) {
  return e3;
}, qh.parseParenArrowList = function(e3, t2, s2, i2) {
  return this.parseArrowExpression(this.startNodeAt(e3, t2), s2, false, i2);
};
var Kh = [];
qh.parseNew = function() {
  this.containsEsc && this.raiseRecoverable(this.start, "Escape sequence in keyword new");
  var e3 = this.startNode(), t2 = this.parseIdent(true);
  if (this.options.ecmaVersion >= 6 && this.eat(oh.dot)) {
    e3.meta = t2;
    var s2 = this.containsEsc;
    return e3.property = this.parseIdent(true), "target" !== e3.property.name && this.raiseRecoverable(e3.property.start, "The only valid meta property for new is 'new.target'"), s2 && this.raiseRecoverable(e3.start, "'new.target' must not contain escaped characters"), this.allowNewDotTarget || this.raiseRecoverable(e3.start, "'new.target' can only be used in functions and class static block"), this.finishNode(e3, "MetaProperty");
  }
  var i2 = this.start, n2 = this.startLoc;
  return e3.callee = this.parseSubscripts(this.parseExprAtom(null, false, true), i2, n2, true, false), this.eat(oh.parenL) ? e3.arguments = this.parseExprList(oh.parenR, this.options.ecmaVersion >= 8, false) : e3.arguments = Kh, this.finishNode(e3, "NewExpression");
}, qh.parseTemplateElement = function(e3) {
  var t2 = e3.isTagged, s2 = this.startNode();
  return this.type === oh.invalidTemplate ? (t2 || this.raiseRecoverable(this.start, "Bad escape sequence in untagged template literal"), s2.value = { raw: this.value, cooked: null }) : s2.value = { raw: this.input.slice(this.start, this.end).replace(/\r\n?/g, "\n"), cooked: this.value }, this.next(), s2.tail = this.type === oh.backQuote, this.finishNode(s2, "TemplateElement");
}, qh.parseTemplate = function(e3) {
  void 0 === e3 && (e3 = {});
  var t2 = e3.isTagged;
  void 0 === t2 && (t2 = false);
  var s2 = this.startNode();
  this.next(), s2.expressions = [];
  var i2 = this.parseTemplateElement({ isTagged: t2 });
  for (s2.quasis = [i2]; !i2.tail; )
    this.type === oh.eof && this.raise(this.pos, "Unterminated template literal"), this.expect(oh.dollarBraceL), s2.expressions.push(this.parseExpression()), this.expect(oh.braceR), s2.quasis.push(i2 = this.parseTemplateElement({ isTagged: t2 }));
  return this.next(), this.finishNode(s2, "TemplateLiteral");
}, qh.isAsyncProp = function(e3) {
  return !e3.computed && "Identifier" === e3.key.type && "async" === e3.key.name && (this.type === oh.name || this.type === oh.num || this.type === oh.string || this.type === oh.bracketL || this.type.keyword || this.options.ecmaVersion >= 9 && this.type === oh.star) && !ah.test(this.input.slice(this.lastTokEnd, this.start));
}, qh.parseObj = function(e3, t2) {
  var s2 = this.startNode(), i2 = true, n2 = {};
  for (s2.properties = [], this.next(); !this.eat(oh.braceR); ) {
    if (i2)
      i2 = false;
    else if (this.expect(oh.comma), this.options.ecmaVersion >= 5 && this.afterTrailingComma(oh.braceR))
      break;
    var r2 = this.parseProperty(e3, t2);
    e3 || this.checkPropClash(r2, n2, t2), s2.properties.push(r2);
  }
  return this.finishNode(s2, e3 ? "ObjectPattern" : "ObjectExpression");
}, qh.parseProperty = function(e3, t2) {
  var s2, i2, n2, r2, o2 = this.startNode();
  if (this.options.ecmaVersion >= 9 && this.eat(oh.ellipsis))
    return e3 ? (o2.argument = this.parseIdent(false), this.type === oh.comma && this.raiseRecoverable(this.start, "Comma is not permitted after the rest element"), this.finishNode(o2, "RestElement")) : (o2.argument = this.parseMaybeAssign(false, t2), this.type === oh.comma && t2 && t2.trailingComma < 0 && (t2.trailingComma = this.start), this.finishNode(o2, "SpreadElement"));
  this.options.ecmaVersion >= 6 && (o2.method = false, o2.shorthand = false, (e3 || t2) && (n2 = this.start, r2 = this.startLoc), e3 || (s2 = this.eat(oh.star)));
  var a2 = this.containsEsc;
  return this.parsePropertyName(o2), !e3 && !a2 && this.options.ecmaVersion >= 8 && !s2 && this.isAsyncProp(o2) ? (i2 = true, s2 = this.options.ecmaVersion >= 9 && this.eat(oh.star), this.parsePropertyName(o2)) : i2 = false, this.parsePropertyValue(o2, e3, s2, i2, n2, r2, t2, a2), this.finishNode(o2, "Property");
}, qh.parseGetterSetter = function(e3) {
  e3.kind = e3.key.name, this.parsePropertyName(e3), e3.value = this.parseMethod(false);
  var t2 = "get" === e3.kind ? 0 : 1;
  if (e3.value.params.length !== t2) {
    var s2 = e3.value.start;
    "get" === e3.kind ? this.raiseRecoverable(s2, "getter should have no params") : this.raiseRecoverable(s2, "setter should have exactly one param");
  } else
    "set" === e3.kind && "RestElement" === e3.value.params[0].type && this.raiseRecoverable(e3.value.params[0].start, "Setter cannot use rest params");
}, qh.parsePropertyValue = function(e3, t2, s2, i2, n2, r2, o2, a2) {
  (s2 || i2) && this.type === oh.colon && this.unexpected(), this.eat(oh.colon) ? (e3.value = t2 ? this.parseMaybeDefault(this.start, this.startLoc) : this.parseMaybeAssign(false, o2), e3.kind = "init") : this.options.ecmaVersion >= 6 && this.type === oh.parenL ? (t2 && this.unexpected(), e3.kind = "init", e3.method = true, e3.value = this.parseMethod(s2, i2)) : t2 || a2 || !(this.options.ecmaVersion >= 5) || e3.computed || "Identifier" !== e3.key.type || "get" !== e3.key.name && "set" !== e3.key.name || this.type === oh.comma || this.type === oh.braceR || this.type === oh.eq ? this.options.ecmaVersion >= 6 && !e3.computed && "Identifier" === e3.key.type ? ((s2 || i2) && this.unexpected(), this.checkUnreserved(e3.key), "await" !== e3.key.name || this.awaitIdentPos || (this.awaitIdentPos = n2), e3.kind = "init", t2 ? e3.value = this.parseMaybeDefault(n2, r2, this.copyNode(e3.key)) : this.type === oh.eq && o2 ? (o2.shorthandAssign < 0 && (o2.shorthandAssign = this.start), e3.value = this.parseMaybeDefault(n2, r2, this.copyNode(e3.key))) : e3.value = this.copyNode(e3.key), e3.shorthand = true) : this.unexpected() : ((s2 || i2) && this.unexpected(), this.parseGetterSetter(e3));
}, qh.parsePropertyName = function(e3) {
  if (this.options.ecmaVersion >= 6) {
    if (this.eat(oh.bracketL))
      return e3.computed = true, e3.key = this.parseMaybeAssign(), this.expect(oh.bracketR), e3.key;
    e3.computed = false;
  }
  return e3.key = this.type === oh.num || this.type === oh.string ? this.parseExprAtom() : this.parseIdent("never" !== this.options.allowReserved);
}, qh.initFunction = function(e3) {
  e3.id = null, this.options.ecmaVersion >= 6 && (e3.generator = e3.expression = false), this.options.ecmaVersion >= 8 && (e3.async = false);
}, qh.parseMethod = function(e3, t2, s2) {
  var i2 = this.startNode(), n2 = this.yieldPos, r2 = this.awaitPos, o2 = this.awaitIdentPos;
  return this.initFunction(i2), this.options.ecmaVersion >= 6 && (i2.generator = e3), this.options.ecmaVersion >= 8 && (i2.async = !!t2), this.yieldPos = 0, this.awaitPos = 0, this.awaitIdentPos = 0, this.enterScope(64 | Ch(t2, i2.generator) | (s2 ? 128 : 0)), this.expect(oh.parenL), i2.params = this.parseBindingList(oh.parenR, false, this.options.ecmaVersion >= 8), this.checkYieldAwaitInDefaultParams(), this.parseFunctionBody(i2, false, true, false), this.yieldPos = n2, this.awaitPos = r2, this.awaitIdentPos = o2, this.finishNode(i2, "FunctionExpression");
}, qh.parseArrowExpression = function(e3, t2, s2, i2) {
  var n2 = this.yieldPos, r2 = this.awaitPos, o2 = this.awaitIdentPos;
  return this.enterScope(16 | Ch(s2, false)), this.initFunction(e3), this.options.ecmaVersion >= 8 && (e3.async = !!s2), this.yieldPos = 0, this.awaitPos = 0, this.awaitIdentPos = 0, e3.params = this.toAssignableList(t2, true), this.parseFunctionBody(e3, true, false, i2), this.yieldPos = n2, this.awaitPos = r2, this.awaitIdentPos = o2, this.finishNode(e3, "ArrowFunctionExpression");
}, qh.parseFunctionBody = function(e3, t2, s2, i2) {
  var n2 = t2 && this.type !== oh.braceL, r2 = this.strict, o2 = false;
  if (n2)
    e3.body = this.parseMaybeAssign(i2), e3.expression = true, this.checkParams(e3, false);
  else {
    var a2 = this.options.ecmaVersion >= 7 && !this.isSimpleParamList(e3.params);
    r2 && !a2 || (o2 = this.strictDirective(this.end)) && a2 && this.raiseRecoverable(e3.start, "Illegal 'use strict' directive in function with non-simple parameter list");
    var l2 = this.labels;
    this.labels = [], o2 && (this.strict = true), this.checkParams(e3, !r2 && !o2 && !t2 && !s2 && this.isSimpleParamList(e3.params)), this.strict && e3.id && this.checkLValSimple(e3.id, 5), e3.body = this.parseBlock(false, void 0, o2 && !r2), e3.expression = false, this.adaptDirectivePrologue(e3.body.body), this.labels = l2;
  }
  this.exitScope();
}, qh.isSimpleParamList = function(e3) {
  for (var t2 = 0, s2 = e3; t2 < s2.length; t2 += 1) {
    if ("Identifier" !== s2[t2].type)
      return false;
  }
  return true;
}, qh.checkParams = function(e3, t2) {
  for (var s2 = /* @__PURE__ */ Object.create(null), i2 = 0, n2 = e3.params; i2 < n2.length; i2 += 1) {
    var r2 = n2[i2];
    this.checkLValInnerPattern(r2, 1, t2 ? null : s2);
  }
}, qh.parseExprList = function(e3, t2, s2, i2) {
  for (var n2 = [], r2 = true; !this.eat(e3); ) {
    if (r2)
      r2 = false;
    else if (this.expect(oh.comma), t2 && this.afterTrailingComma(e3))
      break;
    var o2 = void 0;
    s2 && this.type === oh.comma ? o2 = null : this.type === oh.ellipsis ? (o2 = this.parseSpread(i2), i2 && this.type === oh.comma && i2.trailingComma < 0 && (i2.trailingComma = this.start)) : o2 = this.parseMaybeAssign(false, i2), n2.push(o2);
  }
  return n2;
}, qh.checkUnreserved = function(e3) {
  var t2 = e3.start, s2 = e3.end, i2 = e3.name;
  (this.inGenerator && "yield" === i2 && this.raiseRecoverable(t2, "Cannot use 'yield' as identifier inside a generator"), this.inAsync && "await" === i2 && this.raiseRecoverable(t2, "Cannot use 'await' as identifier inside an async function"), this.currentThisScope().inClassFieldInit && "arguments" === i2 && this.raiseRecoverable(t2, "Cannot use 'arguments' in class field initializer"), !this.inClassStaticBlock || "arguments" !== i2 && "await" !== i2 || this.raise(t2, "Cannot use " + i2 + " in class static initialization block"), this.keywords.test(i2) && this.raise(t2, "Unexpected keyword '" + i2 + "'"), this.options.ecmaVersion < 6 && -1 !== this.input.slice(t2, s2).indexOf("\\")) || (this.strict ? this.reservedWordsStrict : this.reservedWords).test(i2) && (this.inAsync || "await" !== i2 || this.raiseRecoverable(t2, "Cannot use keyword 'await' outside an async function"), this.raiseRecoverable(t2, "The keyword '" + i2 + "' is reserved"));
}, qh.parseIdent = function(e3) {
  var t2 = this.parseIdentNode();
  return this.next(!!e3), this.finishNode(t2, "Identifier"), e3 || (this.checkUnreserved(t2), "await" !== t2.name || this.awaitIdentPos || (this.awaitIdentPos = t2.start)), t2;
}, qh.parseIdentNode = function() {
  var e3 = this.startNode();
  return this.type === oh.name ? e3.name = this.value : this.type.keyword ? (e3.name = this.type.keyword, "class" !== e3.name && "function" !== e3.name || this.lastTokEnd === this.lastTokStart + 1 && 46 === this.input.charCodeAt(this.lastTokStart) || this.context.pop()) : this.unexpected(), e3;
}, qh.parsePrivateIdent = function() {
  var e3 = this.startNode();
  return this.type === oh.privateId ? e3.name = this.value : this.unexpected(), this.next(), this.finishNode(e3, "PrivateIdentifier"), 0 === this.privateNameStack.length ? this.raise(e3.start, "Private field '#" + e3.name + "' must be declared in an enclosing class") : this.privateNameStack[this.privateNameStack.length - 1].used.push(e3), e3;
}, qh.parseYield = function(e3) {
  this.yieldPos || (this.yieldPos = this.start);
  var t2 = this.startNode();
  return this.next(), this.type === oh.semi || this.canInsertSemicolon() || this.type !== oh.star && !this.type.startsExpr ? (t2.delegate = false, t2.argument = null) : (t2.delegate = this.eat(oh.star), t2.argument = this.parseMaybeAssign(e3)), this.finishNode(t2, "YieldExpression");
}, qh.parseAwait = function(e3) {
  this.awaitPos || (this.awaitPos = this.start);
  var t2 = this.startNode();
  return this.next(), t2.argument = this.parseMaybeUnary(null, true, false, e3), this.finishNode(t2, "AwaitExpression");
};
var Yh = $h.prototype;
Yh.raise = function(e3, t2) {
  var s2 = Ah(this.input, e3);
  t2 += " (" + s2.line + ":" + s2.column + ")";
  var i2 = new SyntaxError(t2);
  throw i2.pos = e3, i2.loc = s2, i2.raisedAt = this.pos, i2;
}, Yh.raiseRecoverable = Yh.raise, Yh.curPosition = function() {
  if (this.options.locations)
    return new vh(this.curLine, this.pos - this.lineStart);
};
var Xh = $h.prototype, Qh = function(e3) {
  this.flags = e3, this.var = [], this.lexical = [], this.functions = [], this.inClassFieldInit = false;
};
Xh.enterScope = function(e3) {
  this.scopeStack.push(new Qh(e3));
}, Xh.exitScope = function() {
  this.scopeStack.pop();
}, Xh.treatFunctionsAsVarInScope = function(e3) {
  return 2 & e3.flags || !this.inModule && 1 & e3.flags;
}, Xh.declareName = function(e3, t2, s2) {
  var i2 = false;
  if (2 === t2) {
    var n2 = this.currentScope();
    i2 = n2.lexical.indexOf(e3) > -1 || n2.functions.indexOf(e3) > -1 || n2.var.indexOf(e3) > -1, n2.lexical.push(e3), this.inModule && 1 & n2.flags && delete this.undefinedExports[e3];
  } else if (4 === t2) {
    this.currentScope().lexical.push(e3);
  } else if (3 === t2) {
    var r2 = this.currentScope();
    i2 = this.treatFunctionsAsVar ? r2.lexical.indexOf(e3) > -1 : r2.lexical.indexOf(e3) > -1 || r2.var.indexOf(e3) > -1, r2.functions.push(e3);
  } else
    for (var o2 = this.scopeStack.length - 1; o2 >= 0; --o2) {
      var a2 = this.scopeStack[o2];
      if (a2.lexical.indexOf(e3) > -1 && !(32 & a2.flags && a2.lexical[0] === e3) || !this.treatFunctionsAsVarInScope(a2) && a2.functions.indexOf(e3) > -1) {
        i2 = true;
        break;
      }
      if (a2.var.push(e3), this.inModule && 1 & a2.flags && delete this.undefinedExports[e3], 259 & a2.flags)
        break;
    }
  i2 && this.raiseRecoverable(s2, "Identifier '" + e3 + "' has already been declared");
}, Xh.checkLocalExport = function(e3) {
  -1 === this.scopeStack[0].lexical.indexOf(e3.name) && -1 === this.scopeStack[0].var.indexOf(e3.name) && (this.undefinedExports[e3.name] = e3);
}, Xh.currentScope = function() {
  return this.scopeStack[this.scopeStack.length - 1];
}, Xh.currentVarScope = function() {
  for (var e3 = this.scopeStack.length - 1; ; e3--) {
    var t2 = this.scopeStack[e3];
    if (259 & t2.flags)
      return t2;
  }
}, Xh.currentThisScope = function() {
  for (var e3 = this.scopeStack.length - 1; ; e3--) {
    var t2 = this.scopeStack[e3];
    if (259 & t2.flags && !(16 & t2.flags))
      return t2;
  }
};
var Zh = function(e3, t2, s2) {
  this.type = "", this.start = t2, this.end = 0, e3.options.locations && (this.loc = new Sh(e3, s2)), e3.options.directSourceFile && (this.sourceFile = e3.options.directSourceFile), e3.options.ranges && (this.range = [t2, 0]);
}, Jh = $h.prototype;
function ec(e3, t2, s2, i2) {
  return e3.type = t2, e3.end = s2, this.options.locations && (e3.loc.end = i2), this.options.ranges && (e3.range[1] = s2), e3;
}
Jh.startNode = function() {
  return new Zh(this, this.start, this.startLoc);
}, Jh.startNodeAt = function(e3, t2) {
  return new Zh(this, e3, t2);
}, Jh.finishNode = function(e3, t2) {
  return ec.call(this, e3, t2, this.lastTokEnd, this.lastTokEndLoc);
}, Jh.finishNodeAt = function(e3, t2, s2, i2) {
  return ec.call(this, e3, t2, s2, i2);
}, Jh.copyNode = function(e3) {
  var t2 = new Zh(this, e3.start, this.startLoc);
  for (var s2 in e3)
    t2[s2] = e3[s2];
  return t2;
};
var tc = "ASCII ASCII_Hex_Digit AHex Alphabetic Alpha Any Assigned Bidi_Control Bidi_C Bidi_Mirrored Bidi_M Case_Ignorable CI Cased Changes_When_Casefolded CWCF Changes_When_Casemapped CWCM Changes_When_Lowercased CWL Changes_When_NFKC_Casefolded CWKCF Changes_When_Titlecased CWT Changes_When_Uppercased CWU Dash Default_Ignorable_Code_Point DI Deprecated Dep Diacritic Dia Emoji Emoji_Component Emoji_Modifier Emoji_Modifier_Base Emoji_Presentation Extender Ext Grapheme_Base Gr_Base Grapheme_Extend Gr_Ext Hex_Digit Hex IDS_Binary_Operator IDSB IDS_Trinary_Operator IDST ID_Continue IDC ID_Start IDS Ideographic Ideo Join_Control Join_C Logical_Order_Exception LOE Lowercase Lower Math Noncharacter_Code_Point NChar Pattern_Syntax Pat_Syn Pattern_White_Space Pat_WS Quotation_Mark QMark Radical Regional_Indicator RI Sentence_Terminal STerm Soft_Dotted SD Terminal_Punctuation Term Unified_Ideograph UIdeo Uppercase Upper Variation_Selector VS White_Space space XID_Continue XIDC XID_Start XIDS", sc = tc + " Extended_Pictographic", ic = sc + " EBase EComp EMod EPres ExtPict", nc = { 9: tc, 10: sc, 11: sc, 12: ic, 13: ic, 14: ic }, rc = { 9: "", 10: "", 11: "", 12: "", 13: "", 14: "Basic_Emoji Emoji_Keycap_Sequence RGI_Emoji_Modifier_Sequence RGI_Emoji_Flag_Sequence RGI_Emoji_Tag_Sequence RGI_Emoji_ZWJ_Sequence RGI_Emoji" }, oc = "Cased_Letter LC Close_Punctuation Pe Connector_Punctuation Pc Control Cc cntrl Currency_Symbol Sc Dash_Punctuation Pd Decimal_Number Nd digit Enclosing_Mark Me Final_Punctuation Pf Format Cf Initial_Punctuation Pi Letter L Letter_Number Nl Line_Separator Zl Lowercase_Letter Ll Mark M Combining_Mark Math_Symbol Sm Modifier_Letter Lm Modifier_Symbol Sk Nonspacing_Mark Mn Number N Open_Punctuation Ps Other C Other_Letter Lo Other_Number No Other_Punctuation Po Other_Symbol So Paragraph_Separator Zp Private_Use Co Punctuation P punct Separator Z Space_Separator Zs Spacing_Mark Mc Surrogate Cs Symbol S Titlecase_Letter Lt Unassigned Cn Uppercase_Letter Lu", ac = "Adlam Adlm Ahom Anatolian_Hieroglyphs Hluw Arabic Arab Armenian Armn Avestan Avst Balinese Bali Bamum Bamu Bassa_Vah Bass Batak Batk Bengali Beng Bhaiksuki Bhks Bopomofo Bopo Brahmi Brah Braille Brai Buginese Bugi Buhid Buhd Canadian_Aboriginal Cans Carian Cari Caucasian_Albanian Aghb Chakma Cakm Cham Cham Cherokee Cher Common Zyyy Coptic Copt Qaac Cuneiform Xsux Cypriot Cprt Cyrillic Cyrl Deseret Dsrt Devanagari Deva Duployan Dupl Egyptian_Hieroglyphs Egyp Elbasan Elba Ethiopic Ethi Georgian Geor Glagolitic Glag Gothic Goth Grantha Gran Greek Grek Gujarati Gujr Gurmukhi Guru Han Hani Hangul Hang Hanunoo Hano Hatran Hatr Hebrew Hebr Hiragana Hira Imperial_Aramaic Armi Inherited Zinh Qaai Inscriptional_Pahlavi Phli Inscriptional_Parthian Prti Javanese Java Kaithi Kthi Kannada Knda Katakana Kana Kayah_Li Kali Kharoshthi Khar Khmer Khmr Khojki Khoj Khudawadi Sind Lao Laoo Latin Latn Lepcha Lepc Limbu Limb Linear_A Lina Linear_B Linb Lisu Lisu Lycian Lyci Lydian Lydi Mahajani Mahj Malayalam Mlym Mandaic Mand Manichaean Mani Marchen Marc Masaram_Gondi Gonm Meetei_Mayek Mtei Mende_Kikakui Mend Meroitic_Cursive Merc Meroitic_Hieroglyphs Mero Miao Plrd Modi Mongolian Mong Mro Mroo Multani Mult Myanmar Mymr Nabataean Nbat New_Tai_Lue Talu Newa Newa Nko Nkoo Nushu Nshu Ogham Ogam Ol_Chiki Olck Old_Hungarian Hung Old_Italic Ital Old_North_Arabian Narb Old_Permic Perm Old_Persian Xpeo Old_South_Arabian Sarb Old_Turkic Orkh Oriya Orya Osage Osge Osmanya Osma Pahawh_Hmong Hmng Palmyrene Palm Pau_Cin_Hau Pauc Phags_Pa Phag Phoenician Phnx Psalter_Pahlavi Phlp Rejang Rjng Runic Runr Samaritan Samr Saurashtra Saur Sharada Shrd Shavian Shaw Siddham Sidd SignWriting Sgnw Sinhala Sinh Sora_Sompeng Sora Soyombo Soyo Sundanese Sund Syloti_Nagri Sylo Syriac Syrc Tagalog Tglg Tagbanwa Tagb Tai_Le Tale Tai_Tham Lana Tai_Viet Tavt Takri Takr Tamil Taml Tangut Tang Telugu Telu Thaana Thaa Thai Thai Tibetan Tibt Tifinagh Tfng Tirhuta Tirh Ugaritic Ugar Vai Vaii Warang_Citi Wara Yi Yiii Zanabazar_Square Zanb", lc = ac + " Dogra Dogr Gunjala_Gondi Gong Hanifi_Rohingya Rohg Makasar Maka Medefaidrin Medf Old_Sogdian Sogo Sogdian Sogd", hc = lc + " Elymaic Elym Nandinagari Nand Nyiakeng_Puachue_Hmong Hmnp Wancho Wcho", cc = hc + " Chorasmian Chrs Diak Dives_Akuru Khitan_Small_Script Kits Yezi Yezidi", uc = cc + " Cypro_Minoan Cpmn Old_Uyghur Ougr Tangsa Tnsa Toto Vithkuqi Vith", dc = { 9: ac, 10: lc, 11: hc, 12: cc, 13: uc, 14: uc + " Hrkt Katakana_Or_Hiragana Kawi Nag_Mundari Nagm Unknown Zzzz" }, pc = {};
function fc(e3) {
  var t2 = pc[e3] = { binary: xh(nc[e3] + " " + oc), binaryOfStrings: xh(rc[e3]), nonBinary: { General_Category: xh(oc), Script: xh(dc[e3]) } };
  t2.nonBinary.Script_Extensions = t2.nonBinary.Script, t2.nonBinary.gc = t2.nonBinary.General_Category, t2.nonBinary.sc = t2.nonBinary.Script, t2.nonBinary.scx = t2.nonBinary.Script_Extensions;
}
for (var mc = 0, gc = [9, 10, 11, 12, 13, 14]; mc < gc.length; mc += 1) {
  fc(gc[mc]);
}
var yc = $h.prototype, xc = function(e3) {
  this.parser = e3, this.validFlags = "gim" + (e3.options.ecmaVersion >= 6 ? "uy" : "") + (e3.options.ecmaVersion >= 9 ? "s" : "") + (e3.options.ecmaVersion >= 13 ? "d" : "") + (e3.options.ecmaVersion >= 15 ? "v" : ""), this.unicodeProperties = pc[e3.options.ecmaVersion >= 14 ? 14 : e3.options.ecmaVersion], this.source = "", this.flags = "", this.start = 0, this.switchU = false, this.switchV = false, this.switchN = false, this.pos = 0, this.lastIntValue = 0, this.lastStringValue = "", this.lastAssertionIsQuantifiable = false, this.numCapturingParens = 0, this.maxBackReference = 0, this.groupNames = [], this.backReferenceNames = [];
};
function Ec(e3) {
  return 36 === e3 || e3 >= 40 && e3 <= 43 || 46 === e3 || 63 === e3 || e3 >= 91 && e3 <= 94 || e3 >= 123 && e3 <= 125;
}
function bc(e3) {
  return e3 >= 65 && e3 <= 90 || e3 >= 97 && e3 <= 122;
}
xc.prototype.reset = function(e3, t2, s2) {
  var i2 = -1 !== s2.indexOf("v"), n2 = -1 !== s2.indexOf("u");
  this.start = 0 | e3, this.source = t2 + "", this.flags = s2, i2 && this.parser.options.ecmaVersion >= 15 ? (this.switchU = true, this.switchV = true, this.switchN = true) : (this.switchU = n2 && this.parser.options.ecmaVersion >= 6, this.switchV = false, this.switchN = n2 && this.parser.options.ecmaVersion >= 9);
}, xc.prototype.raise = function(e3) {
  this.parser.raiseRecoverable(this.start, "Invalid regular expression: /" + this.source + "/: " + e3);
}, xc.prototype.at = function(e3, t2) {
  void 0 === t2 && (t2 = false);
  var s2 = this.source, i2 = s2.length;
  if (e3 >= i2)
    return -1;
  var n2 = s2.charCodeAt(e3);
  if (!t2 && !this.switchU || n2 <= 55295 || n2 >= 57344 || e3 + 1 >= i2)
    return n2;
  var r2 = s2.charCodeAt(e3 + 1);
  return r2 >= 56320 && r2 <= 57343 ? (n2 << 10) + r2 - 56613888 : n2;
}, xc.prototype.nextIndex = function(e3, t2) {
  void 0 === t2 && (t2 = false);
  var s2 = this.source, i2 = s2.length;
  if (e3 >= i2)
    return i2;
  var n2, r2 = s2.charCodeAt(e3);
  return !t2 && !this.switchU || r2 <= 55295 || r2 >= 57344 || e3 + 1 >= i2 || (n2 = s2.charCodeAt(e3 + 1)) < 56320 || n2 > 57343 ? e3 + 1 : e3 + 2;
}, xc.prototype.current = function(e3) {
  return void 0 === e3 && (e3 = false), this.at(this.pos, e3);
}, xc.prototype.lookahead = function(e3) {
  return void 0 === e3 && (e3 = false), this.at(this.nextIndex(this.pos, e3), e3);
}, xc.prototype.advance = function(e3) {
  void 0 === e3 && (e3 = false), this.pos = this.nextIndex(this.pos, e3);
}, xc.prototype.eat = function(e3, t2) {
  return void 0 === t2 && (t2 = false), this.current(t2) === e3 && (this.advance(t2), true);
}, xc.prototype.eatChars = function(e3, t2) {
  void 0 === t2 && (t2 = false);
  for (var s2 = this.pos, i2 = 0, n2 = e3; i2 < n2.length; i2 += 1) {
    var r2 = n2[i2], o2 = this.at(s2, t2);
    if (-1 === o2 || o2 !== r2)
      return false;
    s2 = this.nextIndex(s2, t2);
  }
  return this.pos = s2, true;
}, yc.validateRegExpFlags = function(e3) {
  for (var t2 = e3.validFlags, s2 = e3.flags, i2 = false, n2 = false, r2 = 0; r2 < s2.length; r2++) {
    var o2 = s2.charAt(r2);
    -1 === t2.indexOf(o2) && this.raise(e3.start, "Invalid regular expression flag"), s2.indexOf(o2, r2 + 1) > -1 && this.raise(e3.start, "Duplicate regular expression flag"), "u" === o2 && (i2 = true), "v" === o2 && (n2 = true);
  }
  this.options.ecmaVersion >= 15 && i2 && n2 && this.raise(e3.start, "Invalid regular expression flag");
}, yc.validateRegExpPattern = function(e3) {
  this.regexp_pattern(e3), !e3.switchN && this.options.ecmaVersion >= 9 && e3.groupNames.length > 0 && (e3.switchN = true, this.regexp_pattern(e3));
}, yc.regexp_pattern = function(e3) {
  e3.pos = 0, e3.lastIntValue = 0, e3.lastStringValue = "", e3.lastAssertionIsQuantifiable = false, e3.numCapturingParens = 0, e3.maxBackReference = 0, e3.groupNames.length = 0, e3.backReferenceNames.length = 0, this.regexp_disjunction(e3), e3.pos !== e3.source.length && (e3.eat(41) && e3.raise("Unmatched ')'"), (e3.eat(93) || e3.eat(125)) && e3.raise("Lone quantifier brackets")), e3.maxBackReference > e3.numCapturingParens && e3.raise("Invalid escape");
  for (var t2 = 0, s2 = e3.backReferenceNames; t2 < s2.length; t2 += 1) {
    var i2 = s2[t2];
    -1 === e3.groupNames.indexOf(i2) && e3.raise("Invalid named capture referenced");
  }
}, yc.regexp_disjunction = function(e3) {
  for (this.regexp_alternative(e3); e3.eat(124); )
    this.regexp_alternative(e3);
  this.regexp_eatQuantifier(e3, true) && e3.raise("Nothing to repeat"), e3.eat(123) && e3.raise("Lone quantifier brackets");
}, yc.regexp_alternative = function(e3) {
  for (; e3.pos < e3.source.length && this.regexp_eatTerm(e3); )
    ;
}, yc.regexp_eatTerm = function(e3) {
  return this.regexp_eatAssertion(e3) ? (e3.lastAssertionIsQuantifiable && this.regexp_eatQuantifier(e3) && e3.switchU && e3.raise("Invalid quantifier"), true) : !!(e3.switchU ? this.regexp_eatAtom(e3) : this.regexp_eatExtendedAtom(e3)) && (this.regexp_eatQuantifier(e3), true);
}, yc.regexp_eatAssertion = function(e3) {
  var t2 = e3.pos;
  if (e3.lastAssertionIsQuantifiable = false, e3.eat(94) || e3.eat(36))
    return true;
  if (e3.eat(92)) {
    if (e3.eat(66) || e3.eat(98))
      return true;
    e3.pos = t2;
  }
  if (e3.eat(40) && e3.eat(63)) {
    var s2 = false;
    if (this.options.ecmaVersion >= 9 && (s2 = e3.eat(60)), e3.eat(61) || e3.eat(33))
      return this.regexp_disjunction(e3), e3.eat(41) || e3.raise("Unterminated group"), e3.lastAssertionIsQuantifiable = !s2, true;
  }
  return e3.pos = t2, false;
}, yc.regexp_eatQuantifier = function(e3, t2) {
  return void 0 === t2 && (t2 = false), !!this.regexp_eatQuantifierPrefix(e3, t2) && (e3.eat(63), true);
}, yc.regexp_eatQuantifierPrefix = function(e3, t2) {
  return e3.eat(42) || e3.eat(43) || e3.eat(63) || this.regexp_eatBracedQuantifier(e3, t2);
}, yc.regexp_eatBracedQuantifier = function(e3, t2) {
  var s2 = e3.pos;
  if (e3.eat(123)) {
    var i2 = 0, n2 = -1;
    if (this.regexp_eatDecimalDigits(e3) && (i2 = e3.lastIntValue, e3.eat(44) && this.regexp_eatDecimalDigits(e3) && (n2 = e3.lastIntValue), e3.eat(125)))
      return -1 !== n2 && n2 < i2 && !t2 && e3.raise("numbers out of order in {} quantifier"), true;
    e3.switchU && !t2 && e3.raise("Incomplete quantifier"), e3.pos = s2;
  }
  return false;
}, yc.regexp_eatAtom = function(e3) {
  return this.regexp_eatPatternCharacters(e3) || e3.eat(46) || this.regexp_eatReverseSolidusAtomEscape(e3) || this.regexp_eatCharacterClass(e3) || this.regexp_eatUncapturingGroup(e3) || this.regexp_eatCapturingGroup(e3);
}, yc.regexp_eatReverseSolidusAtomEscape = function(e3) {
  var t2 = e3.pos;
  if (e3.eat(92)) {
    if (this.regexp_eatAtomEscape(e3))
      return true;
    e3.pos = t2;
  }
  return false;
}, yc.regexp_eatUncapturingGroup = function(e3) {
  var t2 = e3.pos;
  if (e3.eat(40)) {
    if (e3.eat(63) && e3.eat(58)) {
      if (this.regexp_disjunction(e3), e3.eat(41))
        return true;
      e3.raise("Unterminated group");
    }
    e3.pos = t2;
  }
  return false;
}, yc.regexp_eatCapturingGroup = function(e3) {
  if (e3.eat(40)) {
    if (this.options.ecmaVersion >= 9 ? this.regexp_groupSpecifier(e3) : 63 === e3.current() && e3.raise("Invalid group"), this.regexp_disjunction(e3), e3.eat(41))
      return e3.numCapturingParens += 1, true;
    e3.raise("Unterminated group");
  }
  return false;
}, yc.regexp_eatExtendedAtom = function(e3) {
  return e3.eat(46) || this.regexp_eatReverseSolidusAtomEscape(e3) || this.regexp_eatCharacterClass(e3) || this.regexp_eatUncapturingGroup(e3) || this.regexp_eatCapturingGroup(e3) || this.regexp_eatInvalidBracedQuantifier(e3) || this.regexp_eatExtendedPatternCharacter(e3);
}, yc.regexp_eatInvalidBracedQuantifier = function(e3) {
  return this.regexp_eatBracedQuantifier(e3, true) && e3.raise("Nothing to repeat"), false;
}, yc.regexp_eatSyntaxCharacter = function(e3) {
  var t2 = e3.current();
  return !!Ec(t2) && (e3.lastIntValue = t2, e3.advance(), true);
}, yc.regexp_eatPatternCharacters = function(e3) {
  for (var t2 = e3.pos, s2 = 0; -1 !== (s2 = e3.current()) && !Ec(s2); )
    e3.advance();
  return e3.pos !== t2;
}, yc.regexp_eatExtendedPatternCharacter = function(e3) {
  var t2 = e3.current();
  return !(-1 === t2 || 36 === t2 || t2 >= 40 && t2 <= 43 || 46 === t2 || 63 === t2 || 91 === t2 || 94 === t2 || 124 === t2) && (e3.advance(), true);
}, yc.regexp_groupSpecifier = function(e3) {
  if (e3.eat(63)) {
    if (this.regexp_eatGroupName(e3))
      return -1 !== e3.groupNames.indexOf(e3.lastStringValue) && e3.raise("Duplicate capture group name"), void e3.groupNames.push(e3.lastStringValue);
    e3.raise("Invalid group");
  }
}, yc.regexp_eatGroupName = function(e3) {
  if (e3.lastStringValue = "", e3.eat(60)) {
    if (this.regexp_eatRegExpIdentifierName(e3) && e3.eat(62))
      return true;
    e3.raise("Invalid capture group name");
  }
  return false;
}, yc.regexp_eatRegExpIdentifierName = function(e3) {
  if (e3.lastStringValue = "", this.regexp_eatRegExpIdentifierStart(e3)) {
    for (e3.lastStringValue += Eh(e3.lastIntValue); this.regexp_eatRegExpIdentifierPart(e3); )
      e3.lastStringValue += Eh(e3.lastIntValue);
    return true;
  }
  return false;
}, yc.regexp_eatRegExpIdentifierStart = function(e3) {
  var t2 = e3.pos, s2 = this.options.ecmaVersion >= 11, i2 = e3.current(s2);
  return e3.advance(s2), 92 === i2 && this.regexp_eatRegExpUnicodeEscapeSequence(e3, s2) && (i2 = e3.lastIntValue), function(e4) {
    return Zl(e4, true) || 36 === e4 || 95 === e4;
  }(i2) ? (e3.lastIntValue = i2, true) : (e3.pos = t2, false);
}, yc.regexp_eatRegExpIdentifierPart = function(e3) {
  var t2 = e3.pos, s2 = this.options.ecmaVersion >= 11, i2 = e3.current(s2);
  return e3.advance(s2), 92 === i2 && this.regexp_eatRegExpUnicodeEscapeSequence(e3, s2) && (i2 = e3.lastIntValue), function(e4) {
    return Jl(e4, true) || 36 === e4 || 95 === e4 || 8204 === e4 || 8205 === e4;
  }(i2) ? (e3.lastIntValue = i2, true) : (e3.pos = t2, false);
}, yc.regexp_eatAtomEscape = function(e3) {
  return !!(this.regexp_eatBackReference(e3) || this.regexp_eatCharacterClassEscape(e3) || this.regexp_eatCharacterEscape(e3) || e3.switchN && this.regexp_eatKGroupName(e3)) || (e3.switchU && (99 === e3.current() && e3.raise("Invalid unicode escape"), e3.raise("Invalid escape")), false);
}, yc.regexp_eatBackReference = function(e3) {
  var t2 = e3.pos;
  if (this.regexp_eatDecimalEscape(e3)) {
    var s2 = e3.lastIntValue;
    if (e3.switchU)
      return s2 > e3.maxBackReference && (e3.maxBackReference = s2), true;
    if (s2 <= e3.numCapturingParens)
      return true;
    e3.pos = t2;
  }
  return false;
}, yc.regexp_eatKGroupName = function(e3) {
  if (e3.eat(107)) {
    if (this.regexp_eatGroupName(e3))
      return e3.backReferenceNames.push(e3.lastStringValue), true;
    e3.raise("Invalid named reference");
  }
  return false;
}, yc.regexp_eatCharacterEscape = function(e3) {
  return this.regexp_eatControlEscape(e3) || this.regexp_eatCControlLetter(e3) || this.regexp_eatZero(e3) || this.regexp_eatHexEscapeSequence(e3) || this.regexp_eatRegExpUnicodeEscapeSequence(e3, false) || !e3.switchU && this.regexp_eatLegacyOctalEscapeSequence(e3) || this.regexp_eatIdentityEscape(e3);
}, yc.regexp_eatCControlLetter = function(e3) {
  var t2 = e3.pos;
  if (e3.eat(99)) {
    if (this.regexp_eatControlLetter(e3))
      return true;
    e3.pos = t2;
  }
  return false;
}, yc.regexp_eatZero = function(e3) {
  return 48 === e3.current() && !Ac(e3.lookahead()) && (e3.lastIntValue = 0, e3.advance(), true);
}, yc.regexp_eatControlEscape = function(e3) {
  var t2 = e3.current();
  return 116 === t2 ? (e3.lastIntValue = 9, e3.advance(), true) : 110 === t2 ? (e3.lastIntValue = 10, e3.advance(), true) : 118 === t2 ? (e3.lastIntValue = 11, e3.advance(), true) : 102 === t2 ? (e3.lastIntValue = 12, e3.advance(), true) : 114 === t2 && (e3.lastIntValue = 13, e3.advance(), true);
}, yc.regexp_eatControlLetter = function(e3) {
  var t2 = e3.current();
  return !!bc(t2) && (e3.lastIntValue = t2 % 32, e3.advance(), true);
}, yc.regexp_eatRegExpUnicodeEscapeSequence = function(e3, t2) {
  void 0 === t2 && (t2 = false);
  var s2, i2 = e3.pos, n2 = t2 || e3.switchU;
  if (e3.eat(117)) {
    if (this.regexp_eatFixedHexDigits(e3, 4)) {
      var r2 = e3.lastIntValue;
      if (n2 && r2 >= 55296 && r2 <= 56319) {
        var o2 = e3.pos;
        if (e3.eat(92) && e3.eat(117) && this.regexp_eatFixedHexDigits(e3, 4)) {
          var a2 = e3.lastIntValue;
          if (a2 >= 56320 && a2 <= 57343)
            return e3.lastIntValue = 1024 * (r2 - 55296) + (a2 - 56320) + 65536, true;
        }
        e3.pos = o2, e3.lastIntValue = r2;
      }
      return true;
    }
    if (n2 && e3.eat(123) && this.regexp_eatHexDigits(e3) && e3.eat(125) && ((s2 = e3.lastIntValue) >= 0 && s2 <= 1114111))
      return true;
    n2 && e3.raise("Invalid unicode escape"), e3.pos = i2;
  }
  return false;
}, yc.regexp_eatIdentityEscape = function(e3) {
  if (e3.switchU)
    return !!this.regexp_eatSyntaxCharacter(e3) || !!e3.eat(47) && (e3.lastIntValue = 47, true);
  var t2 = e3.current();
  return !(99 === t2 || e3.switchN && 107 === t2) && (e3.lastIntValue = t2, e3.advance(), true);
}, yc.regexp_eatDecimalEscape = function(e3) {
  e3.lastIntValue = 0;
  var t2 = e3.current();
  if (t2 >= 49 && t2 <= 57) {
    do {
      e3.lastIntValue = 10 * e3.lastIntValue + (t2 - 48), e3.advance();
    } while ((t2 = e3.current()) >= 48 && t2 <= 57);
    return true;
  }
  return false;
};
function vc(e3) {
  return bc(e3) || 95 === e3;
}
function Sc(e3) {
  return vc(e3) || Ac(e3);
}
function Ac(e3) {
  return e3 >= 48 && e3 <= 57;
}
function kc(e3) {
  return e3 >= 48 && e3 <= 57 || e3 >= 65 && e3 <= 70 || e3 >= 97 && e3 <= 102;
}
function Ic(e3) {
  return e3 >= 65 && e3 <= 70 ? e3 - 65 + 10 : e3 >= 97 && e3 <= 102 ? e3 - 97 + 10 : e3 - 48;
}
function wc(e3) {
  return e3 >= 48 && e3 <= 55;
}
yc.regexp_eatCharacterClassEscape = function(e3) {
  var t2 = e3.current();
  if (function(e4) {
    return 100 === e4 || 68 === e4 || 115 === e4 || 83 === e4 || 119 === e4 || 87 === e4;
  }(t2))
    return e3.lastIntValue = -1, e3.advance(), 1;
  var s2 = false;
  if (e3.switchU && this.options.ecmaVersion >= 9 && ((s2 = 80 === t2) || 112 === t2)) {
    var i2;
    if (e3.lastIntValue = -1, e3.advance(), e3.eat(123) && (i2 = this.regexp_eatUnicodePropertyValueExpression(e3)) && e3.eat(125))
      return s2 && 2 === i2 && e3.raise("Invalid property name"), i2;
    e3.raise("Invalid property name");
  }
  return 0;
}, yc.regexp_eatUnicodePropertyValueExpression = function(e3) {
  var t2 = e3.pos;
  if (this.regexp_eatUnicodePropertyName(e3) && e3.eat(61)) {
    var s2 = e3.lastStringValue;
    if (this.regexp_eatUnicodePropertyValue(e3)) {
      var i2 = e3.lastStringValue;
      return this.regexp_validateUnicodePropertyNameAndValue(e3, s2, i2), 1;
    }
  }
  if (e3.pos = t2, this.regexp_eatLoneUnicodePropertyNameOrValue(e3)) {
    var n2 = e3.lastStringValue;
    return this.regexp_validateUnicodePropertyNameOrValue(e3, n2);
  }
  return 0;
}, yc.regexp_validateUnicodePropertyNameAndValue = function(e3, t2, s2) {
  gh(e3.unicodeProperties.nonBinary, t2) || e3.raise("Invalid property name"), e3.unicodeProperties.nonBinary[t2].test(s2) || e3.raise("Invalid property value");
}, yc.regexp_validateUnicodePropertyNameOrValue = function(e3, t2) {
  return e3.unicodeProperties.binary.test(t2) ? 1 : e3.switchV && e3.unicodeProperties.binaryOfStrings.test(t2) ? 2 : void e3.raise("Invalid property name");
}, yc.regexp_eatUnicodePropertyName = function(e3) {
  var t2 = 0;
  for (e3.lastStringValue = ""; vc(t2 = e3.current()); )
    e3.lastStringValue += Eh(t2), e3.advance();
  return "" !== e3.lastStringValue;
}, yc.regexp_eatUnicodePropertyValue = function(e3) {
  var t2 = 0;
  for (e3.lastStringValue = ""; Sc(t2 = e3.current()); )
    e3.lastStringValue += Eh(t2), e3.advance();
  return "" !== e3.lastStringValue;
}, yc.regexp_eatLoneUnicodePropertyNameOrValue = function(e3) {
  return this.regexp_eatUnicodePropertyValue(e3);
}, yc.regexp_eatCharacterClass = function(e3) {
  if (e3.eat(91)) {
    var t2 = e3.eat(94), s2 = this.regexp_classContents(e3);
    return e3.eat(93) || e3.raise("Unterminated character class"), t2 && 2 === s2 && e3.raise("Negated character class may contain strings"), true;
  }
  return false;
}, yc.regexp_classContents = function(e3) {
  return 93 === e3.current() ? 1 : e3.switchV ? this.regexp_classSetExpression(e3) : (this.regexp_nonEmptyClassRanges(e3), 1);
}, yc.regexp_nonEmptyClassRanges = function(e3) {
  for (; this.regexp_eatClassAtom(e3); ) {
    var t2 = e3.lastIntValue;
    if (e3.eat(45) && this.regexp_eatClassAtom(e3)) {
      var s2 = e3.lastIntValue;
      !e3.switchU || -1 !== t2 && -1 !== s2 || e3.raise("Invalid character class"), -1 !== t2 && -1 !== s2 && t2 > s2 && e3.raise("Range out of order in character class");
    }
  }
}, yc.regexp_eatClassAtom = function(e3) {
  var t2 = e3.pos;
  if (e3.eat(92)) {
    if (this.regexp_eatClassEscape(e3))
      return true;
    if (e3.switchU) {
      var s2 = e3.current();
      (99 === s2 || wc(s2)) && e3.raise("Invalid class escape"), e3.raise("Invalid escape");
    }
    e3.pos = t2;
  }
  var i2 = e3.current();
  return 93 !== i2 && (e3.lastIntValue = i2, e3.advance(), true);
}, yc.regexp_eatClassEscape = function(e3) {
  var t2 = e3.pos;
  if (e3.eat(98))
    return e3.lastIntValue = 8, true;
  if (e3.switchU && e3.eat(45))
    return e3.lastIntValue = 45, true;
  if (!e3.switchU && e3.eat(99)) {
    if (this.regexp_eatClassControlLetter(e3))
      return true;
    e3.pos = t2;
  }
  return this.regexp_eatCharacterClassEscape(e3) || this.regexp_eatCharacterEscape(e3);
}, yc.regexp_classSetExpression = function(e3) {
  var t2, s2 = 1;
  if (this.regexp_eatClassSetRange(e3))
    ;
  else if (t2 = this.regexp_eatClassSetOperand(e3)) {
    2 === t2 && (s2 = 2);
    for (var i2 = e3.pos; e3.eatChars([38, 38]); )
      38 !== e3.current() && (t2 = this.regexp_eatClassSetOperand(e3)) ? 2 !== t2 && (s2 = 1) : e3.raise("Invalid character in character class");
    if (i2 !== e3.pos)
      return s2;
    for (; e3.eatChars([45, 45]); )
      this.regexp_eatClassSetOperand(e3) || e3.raise("Invalid character in character class");
    if (i2 !== e3.pos)
      return s2;
  } else
    e3.raise("Invalid character in character class");
  for (; ; )
    if (!this.regexp_eatClassSetRange(e3)) {
      if (!(t2 = this.regexp_eatClassSetOperand(e3)))
        return s2;
      2 === t2 && (s2 = 2);
    }
}, yc.regexp_eatClassSetRange = function(e3) {
  var t2 = e3.pos;
  if (this.regexp_eatClassSetCharacter(e3)) {
    var s2 = e3.lastIntValue;
    if (e3.eat(45) && this.regexp_eatClassSetCharacter(e3)) {
      var i2 = e3.lastIntValue;
      return -1 !== s2 && -1 !== i2 && s2 > i2 && e3.raise("Range out of order in character class"), true;
    }
    e3.pos = t2;
  }
  return false;
}, yc.regexp_eatClassSetOperand = function(e3) {
  return this.regexp_eatClassSetCharacter(e3) ? 1 : this.regexp_eatClassStringDisjunction(e3) || this.regexp_eatNestedClass(e3);
}, yc.regexp_eatNestedClass = function(e3) {
  var t2 = e3.pos;
  if (e3.eat(91)) {
    var s2 = e3.eat(94), i2 = this.regexp_classContents(e3);
    if (e3.eat(93))
      return s2 && 2 === i2 && e3.raise("Negated character class may contain strings"), i2;
    e3.pos = t2;
  }
  if (e3.eat(92)) {
    var n2 = this.regexp_eatCharacterClassEscape(e3);
    if (n2)
      return n2;
    e3.pos = t2;
  }
  return null;
}, yc.regexp_eatClassStringDisjunction = function(e3) {
  var t2 = e3.pos;
  if (e3.eatChars([92, 113])) {
    if (e3.eat(123)) {
      var s2 = this.regexp_classStringDisjunctionContents(e3);
      if (e3.eat(125))
        return s2;
    } else
      e3.raise("Invalid escape");
    e3.pos = t2;
  }
  return null;
}, yc.regexp_classStringDisjunctionContents = function(e3) {
  for (var t2 = this.regexp_classString(e3); e3.eat(124); )
    2 === this.regexp_classString(e3) && (t2 = 2);
  return t2;
}, yc.regexp_classString = function(e3) {
  for (var t2 = 0; this.regexp_eatClassSetCharacter(e3); )
    t2++;
  return 1 === t2 ? 1 : 2;
}, yc.regexp_eatClassSetCharacter = function(e3) {
  var t2 = e3.pos;
  if (e3.eat(92))
    return !(!this.regexp_eatCharacterEscape(e3) && !this.regexp_eatClassSetReservedPunctuator(e3)) || (e3.eat(98) ? (e3.lastIntValue = 8, true) : (e3.pos = t2, false));
  var s2 = e3.current();
  return !(s2 < 0 || s2 === e3.lookahead() && function(e4) {
    return 33 === e4 || e4 >= 35 && e4 <= 38 || e4 >= 42 && e4 <= 44 || 46 === e4 || e4 >= 58 && e4 <= 64 || 94 === e4 || 96 === e4 || 126 === e4;
  }(s2)) && (!function(e4) {
    return 40 === e4 || 41 === e4 || 45 === e4 || 47 === e4 || e4 >= 91 && e4 <= 93 || e4 >= 123 && e4 <= 125;
  }(s2) && (e3.advance(), e3.lastIntValue = s2, true));
}, yc.regexp_eatClassSetReservedPunctuator = function(e3) {
  var t2 = e3.current();
  return !!function(e4) {
    return 33 === e4 || 35 === e4 || 37 === e4 || 38 === e4 || 44 === e4 || 45 === e4 || e4 >= 58 && e4 <= 62 || 64 === e4 || 96 === e4 || 126 === e4;
  }(t2) && (e3.lastIntValue = t2, e3.advance(), true);
}, yc.regexp_eatClassControlLetter = function(e3) {
  var t2 = e3.current();
  return !(!Ac(t2) && 95 !== t2) && (e3.lastIntValue = t2 % 32, e3.advance(), true);
}, yc.regexp_eatHexEscapeSequence = function(e3) {
  var t2 = e3.pos;
  if (e3.eat(120)) {
    if (this.regexp_eatFixedHexDigits(e3, 2))
      return true;
    e3.switchU && e3.raise("Invalid escape"), e3.pos = t2;
  }
  return false;
}, yc.regexp_eatDecimalDigits = function(e3) {
  var t2 = e3.pos, s2 = 0;
  for (e3.lastIntValue = 0; Ac(s2 = e3.current()); )
    e3.lastIntValue = 10 * e3.lastIntValue + (s2 - 48), e3.advance();
  return e3.pos !== t2;
}, yc.regexp_eatHexDigits = function(e3) {
  var t2 = e3.pos, s2 = 0;
  for (e3.lastIntValue = 0; kc(s2 = e3.current()); )
    e3.lastIntValue = 16 * e3.lastIntValue + Ic(s2), e3.advance();
  return e3.pos !== t2;
}, yc.regexp_eatLegacyOctalEscapeSequence = function(e3) {
  if (this.regexp_eatOctalDigit(e3)) {
    var t2 = e3.lastIntValue;
    if (this.regexp_eatOctalDigit(e3)) {
      var s2 = e3.lastIntValue;
      t2 <= 3 && this.regexp_eatOctalDigit(e3) ? e3.lastIntValue = 64 * t2 + 8 * s2 + e3.lastIntValue : e3.lastIntValue = 8 * t2 + s2;
    } else
      e3.lastIntValue = t2;
    return true;
  }
  return false;
}, yc.regexp_eatOctalDigit = function(e3) {
  var t2 = e3.current();
  return wc(t2) ? (e3.lastIntValue = t2 - 48, e3.advance(), true) : (e3.lastIntValue = 0, false);
}, yc.regexp_eatFixedHexDigits = function(e3, t2) {
  var s2 = e3.pos;
  e3.lastIntValue = 0;
  for (var i2 = 0; i2 < t2; ++i2) {
    var n2 = e3.current();
    if (!kc(n2))
      return e3.pos = s2, false;
    e3.lastIntValue = 16 * e3.lastIntValue + Ic(n2), e3.advance();
  }
  return true;
};
var Pc = function(e3) {
  this.type = e3.type, this.value = e3.value, this.start = e3.start, this.end = e3.end, e3.options.locations && (this.loc = new Sh(e3, e3.startLoc, e3.endLoc)), e3.options.ranges && (this.range = [e3.start, e3.end]);
}, Cc = $h.prototype;
function $c(e3) {
  return "function" != typeof BigInt ? null : BigInt(e3.replace(/_/g, ""));
}
Cc.next = function(e3) {
  !e3 && this.type.keyword && this.containsEsc && this.raiseRecoverable(this.start, "Escape sequence in keyword " + this.type.keyword), this.options.onToken && this.options.onToken(new Pc(this)), this.lastTokEnd = this.end, this.lastTokStart = this.start, this.lastTokEndLoc = this.endLoc, this.lastTokStartLoc = this.startLoc, this.nextToken();
}, Cc.getToken = function() {
  return this.next(), new Pc(this);
}, "undefined" != typeof Symbol && (Cc[Symbol.iterator] = function() {
  var e3 = this;
  return { next: function() {
    var t2 = e3.getToken();
    return { done: t2.type === oh.eof, value: t2 };
  } };
}), Cc.nextToken = function() {
  var e3 = this.curContext();
  return e3 && e3.preserveSpace || this.skipSpace(), this.start = this.pos, this.options.locations && (this.startLoc = this.curPosition()), this.pos >= this.input.length ? this.finishToken(oh.eof) : e3.override ? e3.override(this) : void this.readToken(this.fullCharCodeAtPos());
}, Cc.readToken = function(e3) {
  return Zl(e3, this.options.ecmaVersion >= 6) || 92 === e3 ? this.readWord() : this.getTokenFromCode(e3);
}, Cc.fullCharCodeAtPos = function() {
  var e3 = this.input.charCodeAt(this.pos);
  if (e3 <= 55295 || e3 >= 56320)
    return e3;
  var t2 = this.input.charCodeAt(this.pos + 1);
  return t2 <= 56319 || t2 >= 57344 ? e3 : (e3 << 10) + t2 - 56613888;
}, Cc.skipBlockComment = function() {
  var e3 = this.options.onComment && this.curPosition(), t2 = this.pos, s2 = this.input.indexOf("*/", this.pos += 2);
  if (-1 === s2 && this.raise(this.pos - 2, "Unterminated comment"), this.pos = s2 + 2, this.options.locations)
    for (var i2 = void 0, n2 = t2; (i2 = ch(this.input, n2, this.pos)) > -1; )
      ++this.curLine, n2 = this.lineStart = i2;
  this.options.onComment && this.options.onComment(true, this.input.slice(t2 + 2, s2), t2, this.pos, e3, this.curPosition());
}, Cc.skipLineComment = function(e3) {
  for (var t2 = this.pos, s2 = this.options.onComment && this.curPosition(), i2 = this.input.charCodeAt(this.pos += e3); this.pos < this.input.length && !hh(i2); )
    i2 = this.input.charCodeAt(++this.pos);
  this.options.onComment && this.options.onComment(false, this.input.slice(t2 + e3, this.pos), t2, this.pos, s2, this.curPosition());
}, Cc.skipSpace = function() {
  e:
    for (; this.pos < this.input.length; ) {
      var e3 = this.input.charCodeAt(this.pos);
      switch (e3) {
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
          if (!(e3 > 8 && e3 < 14 || e3 >= 5760 && uh.test(String.fromCharCode(e3))))
            break e;
          ++this.pos;
      }
    }
}, Cc.finishToken = function(e3, t2) {
  this.end = this.pos, this.options.locations && (this.endLoc = this.curPosition());
  var s2 = this.type;
  this.type = e3, this.value = t2, this.updateContext(s2);
}, Cc.readToken_dot = function() {
  var e3 = this.input.charCodeAt(this.pos + 1);
  if (e3 >= 48 && e3 <= 57)
    return this.readNumber(true);
  var t2 = this.input.charCodeAt(this.pos + 2);
  return this.options.ecmaVersion >= 6 && 46 === e3 && 46 === t2 ? (this.pos += 3, this.finishToken(oh.ellipsis)) : (++this.pos, this.finishToken(oh.dot));
}, Cc.readToken_slash = function() {
  var e3 = this.input.charCodeAt(this.pos + 1);
  return this.exprAllowed ? (++this.pos, this.readRegexp()) : 61 === e3 ? this.finishOp(oh.assign, 2) : this.finishOp(oh.slash, 1);
}, Cc.readToken_mult_modulo_exp = function(e3) {
  var t2 = this.input.charCodeAt(this.pos + 1), s2 = 1, i2 = 42 === e3 ? oh.star : oh.modulo;
  return this.options.ecmaVersion >= 7 && 42 === e3 && 42 === t2 && (++s2, i2 = oh.starstar, t2 = this.input.charCodeAt(this.pos + 2)), 61 === t2 ? this.finishOp(oh.assign, s2 + 1) : this.finishOp(i2, s2);
}, Cc.readToken_pipe_amp = function(e3) {
  var t2 = this.input.charCodeAt(this.pos + 1);
  if (t2 === e3) {
    if (this.options.ecmaVersion >= 12) {
      if (61 === this.input.charCodeAt(this.pos + 2))
        return this.finishOp(oh.assign, 3);
    }
    return this.finishOp(124 === e3 ? oh.logicalOR : oh.logicalAND, 2);
  }
  return 61 === t2 ? this.finishOp(oh.assign, 2) : this.finishOp(124 === e3 ? oh.bitwiseOR : oh.bitwiseAND, 1);
}, Cc.readToken_caret = function() {
  return 61 === this.input.charCodeAt(this.pos + 1) ? this.finishOp(oh.assign, 2) : this.finishOp(oh.bitwiseXOR, 1);
}, Cc.readToken_plus_min = function(e3) {
  var t2 = this.input.charCodeAt(this.pos + 1);
  return t2 === e3 ? 45 !== t2 || this.inModule || 62 !== this.input.charCodeAt(this.pos + 2) || 0 !== this.lastTokEnd && !ah.test(this.input.slice(this.lastTokEnd, this.pos)) ? this.finishOp(oh.incDec, 2) : (this.skipLineComment(3), this.skipSpace(), this.nextToken()) : 61 === t2 ? this.finishOp(oh.assign, 2) : this.finishOp(oh.plusMin, 1);
}, Cc.readToken_lt_gt = function(e3) {
  var t2 = this.input.charCodeAt(this.pos + 1), s2 = 1;
  return t2 === e3 ? (s2 = 62 === e3 && 62 === this.input.charCodeAt(this.pos + 2) ? 3 : 2, 61 === this.input.charCodeAt(this.pos + s2) ? this.finishOp(oh.assign, s2 + 1) : this.finishOp(oh.bitShift, s2)) : 33 !== t2 || 60 !== e3 || this.inModule || 45 !== this.input.charCodeAt(this.pos + 2) || 45 !== this.input.charCodeAt(this.pos + 3) ? (61 === t2 && (s2 = 2), this.finishOp(oh.relational, s2)) : (this.skipLineComment(4), this.skipSpace(), this.nextToken());
}, Cc.readToken_eq_excl = function(e3) {
  var t2 = this.input.charCodeAt(this.pos + 1);
  return 61 === t2 ? this.finishOp(oh.equality, 61 === this.input.charCodeAt(this.pos + 2) ? 3 : 2) : 61 === e3 && 62 === t2 && this.options.ecmaVersion >= 6 ? (this.pos += 2, this.finishToken(oh.arrow)) : this.finishOp(61 === e3 ? oh.eq : oh.prefix, 1);
}, Cc.readToken_question = function() {
  var e3 = this.options.ecmaVersion;
  if (e3 >= 11) {
    var t2 = this.input.charCodeAt(this.pos + 1);
    if (46 === t2) {
      var s2 = this.input.charCodeAt(this.pos + 2);
      if (s2 < 48 || s2 > 57)
        return this.finishOp(oh.questionDot, 2);
    }
    if (63 === t2) {
      if (e3 >= 12) {
        if (61 === this.input.charCodeAt(this.pos + 2))
          return this.finishOp(oh.assign, 3);
      }
      return this.finishOp(oh.coalesce, 2);
    }
  }
  return this.finishOp(oh.question, 1);
}, Cc.readToken_numberSign = function() {
  var e3 = 35;
  if (this.options.ecmaVersion >= 13 && (++this.pos, Zl(e3 = this.fullCharCodeAtPos(), true) || 92 === e3))
    return this.finishToken(oh.privateId, this.readWord1());
  this.raise(this.pos, "Unexpected character '" + Eh(e3) + "'");
}, Cc.getTokenFromCode = function(e3) {
  switch (e3) {
    case 46:
      return this.readToken_dot();
    case 40:
      return ++this.pos, this.finishToken(oh.parenL);
    case 41:
      return ++this.pos, this.finishToken(oh.parenR);
    case 59:
      return ++this.pos, this.finishToken(oh.semi);
    case 44:
      return ++this.pos, this.finishToken(oh.comma);
    case 91:
      return ++this.pos, this.finishToken(oh.bracketL);
    case 93:
      return ++this.pos, this.finishToken(oh.bracketR);
    case 123:
      return ++this.pos, this.finishToken(oh.braceL);
    case 125:
      return ++this.pos, this.finishToken(oh.braceR);
    case 58:
      return ++this.pos, this.finishToken(oh.colon);
    case 96:
      if (this.options.ecmaVersion < 6)
        break;
      return ++this.pos, this.finishToken(oh.backQuote);
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
      return this.readString(e3);
    case 47:
      return this.readToken_slash();
    case 37:
    case 42:
      return this.readToken_mult_modulo_exp(e3);
    case 124:
    case 38:
      return this.readToken_pipe_amp(e3);
    case 94:
      return this.readToken_caret();
    case 43:
    case 45:
      return this.readToken_plus_min(e3);
    case 60:
    case 62:
      return this.readToken_lt_gt(e3);
    case 61:
    case 33:
      return this.readToken_eq_excl(e3);
    case 63:
      return this.readToken_question();
    case 126:
      return this.finishOp(oh.prefix, 1);
    case 35:
      return this.readToken_numberSign();
  }
  this.raise(this.pos, "Unexpected character '" + Eh(e3) + "'");
}, Cc.finishOp = function(e3, t2) {
  var s2 = this.input.slice(this.pos, this.pos + t2);
  return this.pos += t2, this.finishToken(e3, s2);
}, Cc.readRegexp = function() {
  for (var e3, t2, s2 = this.pos; ; ) {
    this.pos >= this.input.length && this.raise(s2, "Unterminated regular expression");
    var i2 = this.input.charAt(this.pos);
    if (ah.test(i2) && this.raise(s2, "Unterminated regular expression"), e3)
      e3 = false;
    else {
      if ("[" === i2)
        t2 = true;
      else if ("]" === i2 && t2)
        t2 = false;
      else if ("/" === i2 && !t2)
        break;
      e3 = "\\" === i2;
    }
    ++this.pos;
  }
  var n2 = this.input.slice(s2, this.pos);
  ++this.pos;
  var r2 = this.pos, o2 = this.readWord1();
  this.containsEsc && this.unexpected(r2);
  var a2 = this.regexpState || (this.regexpState = new xc(this));
  a2.reset(s2, n2, o2), this.validateRegExpFlags(a2), this.validateRegExpPattern(a2);
  var l2 = null;
  try {
    l2 = new RegExp(n2, o2);
  } catch (e4) {
  }
  return this.finishToken(oh.regexp, { pattern: n2, flags: o2, value: l2 });
}, Cc.readInt = function(e3, t2, s2) {
  for (var i2 = this.options.ecmaVersion >= 12 && void 0 === t2, n2 = s2 && 48 === this.input.charCodeAt(this.pos), r2 = this.pos, o2 = 0, a2 = 0, l2 = 0, h2 = null == t2 ? 1 / 0 : t2; l2 < h2; ++l2, ++this.pos) {
    var c2 = this.input.charCodeAt(this.pos), u2 = void 0;
    if (i2 && 95 === c2)
      n2 && this.raiseRecoverable(this.pos, "Numeric separator is not allowed in legacy octal numeric literals"), 95 === a2 && this.raiseRecoverable(this.pos, "Numeric separator must be exactly one underscore"), 0 === l2 && this.raiseRecoverable(this.pos, "Numeric separator is not allowed at the first of digits"), a2 = c2;
    else {
      if ((u2 = c2 >= 97 ? c2 - 97 + 10 : c2 >= 65 ? c2 - 65 + 10 : c2 >= 48 && c2 <= 57 ? c2 - 48 : 1 / 0) >= e3)
        break;
      a2 = c2, o2 = o2 * e3 + u2;
    }
  }
  return i2 && 95 === a2 && this.raiseRecoverable(this.pos - 1, "Numeric separator is not allowed at the last of digits"), this.pos === r2 || null != t2 && this.pos - r2 !== t2 ? null : o2;
}, Cc.readRadixNumber = function(e3) {
  var t2 = this.pos;
  this.pos += 2;
  var s2 = this.readInt(e3);
  return null == s2 && this.raise(this.start + 2, "Expected number in radix " + e3), this.options.ecmaVersion >= 11 && 110 === this.input.charCodeAt(this.pos) ? (s2 = $c(this.input.slice(t2, this.pos)), ++this.pos) : Zl(this.fullCharCodeAtPos()) && this.raise(this.pos, "Identifier directly after number"), this.finishToken(oh.num, s2);
}, Cc.readNumber = function(e3) {
  var t2 = this.pos;
  e3 || null !== this.readInt(10, void 0, true) || this.raise(t2, "Invalid number");
  var s2 = this.pos - t2 >= 2 && 48 === this.input.charCodeAt(t2);
  s2 && this.strict && this.raise(t2, "Invalid number");
  var i2 = this.input.charCodeAt(this.pos);
  if (!s2 && !e3 && this.options.ecmaVersion >= 11 && 110 === i2) {
    var n2 = $c(this.input.slice(t2, this.pos));
    return ++this.pos, Zl(this.fullCharCodeAtPos()) && this.raise(this.pos, "Identifier directly after number"), this.finishToken(oh.num, n2);
  }
  s2 && /[89]/.test(this.input.slice(t2, this.pos)) && (s2 = false), 46 !== i2 || s2 || (++this.pos, this.readInt(10), i2 = this.input.charCodeAt(this.pos)), 69 !== i2 && 101 !== i2 || s2 || (43 !== (i2 = this.input.charCodeAt(++this.pos)) && 45 !== i2 || ++this.pos, null === this.readInt(10) && this.raise(t2, "Invalid number")), Zl(this.fullCharCodeAtPos()) && this.raise(this.pos, "Identifier directly after number");
  var r2, o2 = (r2 = this.input.slice(t2, this.pos), s2 ? parseInt(r2, 8) : parseFloat(r2.replace(/_/g, "")));
  return this.finishToken(oh.num, o2);
}, Cc.readCodePoint = function() {
  var e3;
  if (123 === this.input.charCodeAt(this.pos)) {
    this.options.ecmaVersion < 6 && this.unexpected();
    var t2 = ++this.pos;
    e3 = this.readHexChar(this.input.indexOf("}", this.pos) - this.pos), ++this.pos, e3 > 1114111 && this.invalidStringToken(t2, "Code point out of bounds");
  } else
    e3 = this.readHexChar(4);
  return e3;
}, Cc.readString = function(e3) {
  for (var t2 = "", s2 = ++this.pos; ; ) {
    this.pos >= this.input.length && this.raise(this.start, "Unterminated string constant");
    var i2 = this.input.charCodeAt(this.pos);
    if (i2 === e3)
      break;
    92 === i2 ? (t2 += this.input.slice(s2, this.pos), t2 += this.readEscapedChar(false), s2 = this.pos) : 8232 === i2 || 8233 === i2 ? (this.options.ecmaVersion < 10 && this.raise(this.start, "Unterminated string constant"), ++this.pos, this.options.locations && (this.curLine++, this.lineStart = this.pos)) : (hh(i2) && this.raise(this.start, "Unterminated string constant"), ++this.pos);
  }
  return t2 += this.input.slice(s2, this.pos++), this.finishToken(oh.string, t2);
};
var Nc = {};
Cc.tryReadTemplateToken = function() {
  this.inTemplateElement = true;
  try {
    this.readTmplToken();
  } catch (e3) {
    if (e3 !== Nc)
      throw e3;
    this.readInvalidTemplateToken();
  }
  this.inTemplateElement = false;
}, Cc.invalidStringToken = function(e3, t2) {
  if (this.inTemplateElement && this.options.ecmaVersion >= 9)
    throw Nc;
  this.raise(e3, t2);
}, Cc.readTmplToken = function() {
  for (var e3 = "", t2 = this.pos; ; ) {
    this.pos >= this.input.length && this.raise(this.start, "Unterminated template");
    var s2 = this.input.charCodeAt(this.pos);
    if (96 === s2 || 36 === s2 && 123 === this.input.charCodeAt(this.pos + 1))
      return this.pos !== this.start || this.type !== oh.template && this.type !== oh.invalidTemplate ? (e3 += this.input.slice(t2, this.pos), this.finishToken(oh.template, e3)) : 36 === s2 ? (this.pos += 2, this.finishToken(oh.dollarBraceL)) : (++this.pos, this.finishToken(oh.backQuote));
    if (92 === s2)
      e3 += this.input.slice(t2, this.pos), e3 += this.readEscapedChar(true), t2 = this.pos;
    else if (hh(s2)) {
      switch (e3 += this.input.slice(t2, this.pos), ++this.pos, s2) {
        case 13:
          10 === this.input.charCodeAt(this.pos) && ++this.pos;
        case 10:
          e3 += "\n";
          break;
        default:
          e3 += String.fromCharCode(s2);
      }
      this.options.locations && (++this.curLine, this.lineStart = this.pos), t2 = this.pos;
    } else
      ++this.pos;
  }
}, Cc.readInvalidTemplateToken = function() {
  for (; this.pos < this.input.length; this.pos++)
    switch (this.input[this.pos]) {
      case "\\":
        ++this.pos;
        break;
      case "$":
        if ("{" !== this.input[this.pos + 1])
          break;
      case "`":
        return this.finishToken(oh.invalidTemplate, this.input.slice(this.start, this.pos));
    }
  this.raise(this.start, "Unterminated template");
}, Cc.readEscapedChar = function(e3) {
  var t2 = this.input.charCodeAt(++this.pos);
  switch (++this.pos, t2) {
    case 110:
      return "\n";
    case 114:
      return "\r";
    case 120:
      return String.fromCharCode(this.readHexChar(2));
    case 117:
      return Eh(this.readCodePoint());
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
      if (this.strict && this.invalidStringToken(this.pos - 1, "Invalid escape sequence"), e3) {
        var s2 = this.pos - 1;
        this.invalidStringToken(s2, "Invalid escape sequence in template string");
      }
    default:
      if (t2 >= 48 && t2 <= 55) {
        var i2 = this.input.substr(this.pos - 1, 3).match(/^[0-7]+/)[0], n2 = parseInt(i2, 8);
        return n2 > 255 && (i2 = i2.slice(0, -1), n2 = parseInt(i2, 8)), this.pos += i2.length - 1, t2 = this.input.charCodeAt(this.pos), "0" === i2 && 56 !== t2 && 57 !== t2 || !this.strict && !e3 || this.invalidStringToken(this.pos - 1 - i2.length, e3 ? "Octal literal in template string" : "Octal literal in strict mode"), String.fromCharCode(n2);
      }
      return hh(t2) ? "" : String.fromCharCode(t2);
  }
}, Cc.readHexChar = function(e3) {
  var t2 = this.pos, s2 = this.readInt(16, e3);
  return null === s2 && this.invalidStringToken(t2, "Bad character escape sequence"), s2;
}, Cc.readWord1 = function() {
  this.containsEsc = false;
  for (var e3 = "", t2 = true, s2 = this.pos, i2 = this.options.ecmaVersion >= 6; this.pos < this.input.length; ) {
    var n2 = this.fullCharCodeAtPos();
    if (Jl(n2, i2))
      this.pos += n2 <= 65535 ? 1 : 2;
    else {
      if (92 !== n2)
        break;
      this.containsEsc = true, e3 += this.input.slice(s2, this.pos);
      var r2 = this.pos;
      117 !== this.input.charCodeAt(++this.pos) && this.invalidStringToken(this.pos, "Expecting Unicode escape sequence \\uXXXX"), ++this.pos;
      var o2 = this.readCodePoint();
      (t2 ? Zl : Jl)(o2, i2) || this.invalidStringToken(r2, "Invalid Unicode escape"), e3 += Eh(o2), s2 = this.pos;
    }
    t2 = false;
  }
  return e3 + this.input.slice(s2, this.pos);
}, Cc.readWord = function() {
  var e3 = this.readWord1(), t2 = oh.name;
  return this.keywords.test(e3) && (t2 = nh[e3]), this.finishToken(t2, e3);
};
var _c = "8.9.0";
$h.acorn = { Parser: $h, version: _c, defaultOptions: kh, Position: vh, SourceLocation: Sh, getLineInfo: Ah, Node: Zh, TokenType: eh, tokTypes: oh, keywordTypes: nh, TokContext: Uh, tokContexts: Gh, isIdentifierChar: Jl, isIdentifierStart: Zl, Token: Pc, isNewLine: hh, lineBreak: ah, lineBreakG: lh, nonASCIIwhitespace: uh };
var Rc = Object.freeze({ __proto__: null, Node: Zh, Parser: $h, Position: vh, SourceLocation: Sh, TokContext: Uh, Token: Pc, TokenType: eh, defaultOptions: kh, getLineInfo: Ah, isIdentifierChar: Jl, isIdentifierStart: Zl, isNewLine: hh, keywordTypes: nh, lineBreak: ah, lineBreakG: lh, nonASCIIwhitespace: uh, parse: function(e3, t2) {
  return $h.parse(e3, t2);
}, parseExpressionAt: function(e3, t2, s2) {
  return $h.parseExpressionAt(e3, t2, s2);
}, tokContexts: Gh, tokTypes: oh, tokenizer: function(e3, t2) {
  return $h.tokenizer(e3, t2);
}, version: _c });
const Oc = (e3) => () => Ye(function(e4) {
  return { code: "NO_FS_IN_BROWSER", message: `Cannot access the file system (via "${e4}") when using the browser build of Rollup. Make sure you supply a plugin with custom resolveId and load hooks to Rollup.`, url: Oe("plugin-development/#a-simple-example") };
}(e3)), Dc = Oc("fs.mkdir"), Lc = Oc("fs.readFile"), Tc = Oc("fs.writeFile");
async function Mc(e3, t2, s2, i2, n2, r2, o2, a2, l2) {
  const h2 = await function(e4, t3, s3, i3, n3, r3, o3, a3) {
    let l3 = null, h3 = null;
    if (n3) {
      l3 = /* @__PURE__ */ new Set();
      for (const s4 of n3)
        e4 === s4.source && t3 === s4.importer && l3.add(s4.plugin);
      h3 = (e5, t4) => ({ ...e5, resolve: (e6, s4, { assertions: r4, custom: o4, isEntry: a4, skipSelf: l4 } = pe) => i3(e6, s4, o4, a4, r4 || fe, l4 ? [...n3, { importer: s4, plugin: t4, source: e6 }] : n3) });
    }
    return s3.hookFirstAndGetPlugin("resolveId", [e4, t3, { assertions: a3, custom: r3, isEntry: o3 }], h3, l3);
  }(e3, t2, i2, n2, r2, o2, a2, l2);
  return null == h2 ? Oc("path.resolve")() : h2[0];
}
const Vc = "at position ", Bc = "at output position ";
const zc = { delete: () => false, get() {
}, has: () => false, set() {
} };
function Fc(e3) {
  return e3.startsWith(Vc) || e3.startsWith(Bc) ? Ye({ code: Je, message: "A plugin is trying to use the Rollup cache but is not declaring a plugin name or cacheKey." }) : Ye({ code: rt, message: `The plugin name ${e3} is being used twice in the same build. Plugin names must be distinct or provide a cacheKey (please post an issue to the plugin if you are a plugin user).` });
}
const jc = (e3, t2, s2 = Hc) => {
  const { onwarn: i2, onLog: n2 } = e3, r2 = Uc(s2, i2);
  if (n2) {
    const e4 = ke[t2];
    return (t3, s3) => n2(t3, Gc(s3), (t4, s4) => {
      if ("error" === t4)
        return Ye(Wc(s4));
      ke[t4] >= e4 && r2(t4, Wc(s4));
    });
  }
  return r2;
}, Uc = (e3, t2) => t2 ? (s2, i2) => {
  s2 === ve ? t2(Gc(i2), (t3) => e3(ve, Wc(t3))) : e3(s2, i2);
} : e3, Gc = (e3) => (Object.defineProperty(e3, "toString", { value: () => qc(e3), writable: true }), e3), Wc = (e3) => "string" == typeof e3 ? { message: e3 } : "function" == typeof e3 ? Wc(e3()) : e3, qc = (e3) => {
  let t2 = "";
  return e3.plugin && (t2 += `(${e3.plugin} plugin) `), e3.loc && (t2 += `${T(e3.loc.file)} (${e3.loc.line}:${e3.loc.column}) `), t2 + e3.message;
}, Hc = (e3, t2) => {
  const s2 = qc(t2);
  switch (e3) {
    case ve:
      return console.warn(s2);
    case Ae:
      return console.debug(s2);
    default:
      return console.info(s2);
  }
};
function Kc(e3, t2, s2, i2, n2 = /$./) {
  const r2 = new Set(t2), o2 = Object.keys(e3).filter((e4) => !(r2.has(e4) || n2.test(e4)));
  o2.length > 0 && i2(ve, function(e4, t3, s3) {
    return { code: Pt, message: `Unknown ${e4}: ${t3.join(", ")}. Allowed options: ${s3.join(", ")}` };
  }(s2, o2, [...r2].sort()));
}
const Yc = { recommended: { annotations: true, correctVarValueBeforeDeclaration: false, manualPureFunctions: me, moduleSideEffects: () => true, propertyReadSideEffects: true, tryCatchDeoptimization: true, unknownGlobalSideEffects: false }, safest: { annotations: true, correctVarValueBeforeDeclaration: true, manualPureFunctions: me, moduleSideEffects: () => true, propertyReadSideEffects: true, tryCatchDeoptimization: true, unknownGlobalSideEffects: true }, smallest: { annotations: true, correctVarValueBeforeDeclaration: false, manualPureFunctions: me, moduleSideEffects: () => false, propertyReadSideEffects: false, tryCatchDeoptimization: false, unknownGlobalSideEffects: false } }, Xc = { es2015: { arrowFunctions: true, constBindings: true, objectShorthand: true, reservedNamesAsProps: true, symbols: true }, es5: { arrowFunctions: false, constBindings: false, objectShorthand: false, reservedNamesAsProps: true, symbols: false } }, Qc = (e3, t2, s2, i2, n2) => {
  const r2 = e3 == null ? void 0 : e3.preset;
  if (r2) {
    const n3 = t2[r2];
    if (n3)
      return { ...n3, ...e3 };
    Ye(zt(`${s2}.preset`, i2, `valid values are ${Re(Object.keys(t2))}`, r2));
  }
  return ((e4, t3, s3, i3) => (n3) => {
    if ("string" == typeof n3) {
      const r3 = e4[n3];
      if (r3)
        return r3;
      Ye(zt(t3, s3, `valid values are ${i3}${Re(Object.keys(e4))}. You can also supply an object for more fine-grained control`, n3));
    }
    return ((e5) => e5 && "object" == typeof e5 ? e5 : {})(n3);
  })(t2, s2, i2, n2)(e3);
}, Zc = async (e3) => (await async function(e4) {
  do {
    e4 = (await Promise.all(e4)).flat(1 / 0);
  } while (e4.some((e5) => e5 == null ? void 0 : e5.then));
  return e4;
}([e3])).filter(Boolean);
async function Jc(e3, t2, s2, i2) {
  const n2 = t2.id, r2 = [];
  let o2 = null === e3.map ? null : Tl(e3.map);
  const a2 = e3.code;
  let h2 = e3.ast;
  const c2 = [], u2 = [];
  let d2 = false;
  const p2 = () => d2 = true;
  let f2 = "", m2 = e3.code;
  const y2 = (e4) => (t3, s3) => {
    t3 = Wc(t3), s3 && Xe(t3, s3, m2, n2), t3.id = n2, t3.hook = "transform", e4(t3);
  };
  let x2;
  try {
    x2 = await s2.hookReduceArg0("transform", [m2, n2], function(e4, s3, n3) {
      let o3, a3;
      if ("string" == typeof s3)
        o3 = s3;
      else {
        if (!s3 || "object" != typeof s3)
          return e4;
        if (t2.updateOptions(s3), null == s3.code)
          return (s3.map || s3.ast) && i2(ve, function(e5) {
            return { code: St, message: `The plugin "${e5}" returned a "map" or "ast" without returning a "code". This will be ignored.` };
          }(n3.name)), e4;
        ({ code: o3, map: a3, ast: h2 } = s3);
      }
      return null !== a3 && r2.push(Tl("string" == typeof a3 ? JSON.parse(a3) : a3) || { missing: true, plugin: n3.name }), m2 = o3, o3;
    }, (e4, t3) => {
      return f2 = t3.name, { ...e4, addWatchFile(t4) {
        c2.push(t4), e4.addWatchFile(t4);
      }, cache: d2 ? e4.cache : (h3 = e4.cache, x3 = p2, { delete: (e5) => (x3(), h3.delete(e5)), get: (e5) => (x3(), h3.get(e5)), has: (e5) => (x3(), h3.has(e5)), set: (e5, t4) => (x3(), h3.set(e5, t4)) }), debug: y2(e4.debug), emitFile: (e5) => (u2.push(e5), s2.emitFile(e5)), error: (t4, s3) => ("string" == typeof t4 && (t4 = { message: t4 }), s3 && Xe(t4, s3, m2, n2), t4.id = n2, t4.hook = "transform", e4.error(t4)), getCombinedSourcemap() {
        const e5 = function(e6, t4, s3, i3, n3) {
          return 0 === i3.length ? s3 : { version: 3, ...Xa(e6, t4, s3, i3, Ya(n3)).traceMappings() };
        }(n2, a2, o2, r2, i2);
        if (!e5) {
          return new g(a2).generateMap({ hires: true, includeContent: true, source: n2 });
        }
        return o2 !== e5 && (o2 = e5, r2.length = 0), new l({ ...e5, file: null, sourcesContent: e5.sourcesContent });
      }, info: y2(e4.info), setAssetSource() {
        return this.error({ code: ft, message: "setAssetSource cannot be called in transform for caching reasons. Use emitFile with a source, or call setAssetSource in another hook." });
      }, warn: y2(e4.warn) };
      var h3, x3;
    });
  } catch (e4) {
    return Ye(Gt(e4, f2, { hook: "transform", id: n2 }));
  }
  return !d2 && u2.length > 0 && (t2.transformFiles = u2), { ast: h2, code: x2, customTransformCache: d2, originalCode: a2, originalSourcemap: o2, sourcemapChain: r2, transformDependencies: c2 };
}
const eu = "resolveDependencies";
class tu {
  constructor(e3, t2, s2, i2) {
    this.graph = e3, this.modulesById = t2, this.options = s2, this.pluginDriver = i2, this.implicitEntryModules = /* @__PURE__ */ new Set(), this.indexedEntryModules = [], this.latestLoadModulesPromise = Promise.resolve(), this.moduleLoadPromises = /* @__PURE__ */ new Map(), this.modulesWithLoadedDependencies = /* @__PURE__ */ new Set(), this.nextChunkNamePriority = 0, this.nextEntryModuleIndex = 0, this.resolveId = async (e4, t3, s3, i3, n2, r2 = null) => this.getResolvedIdWithDefaults(this.getNormalizedResolvedIdWithoutDefaults(!this.options.external(e4, t3, false) && await Mc(e4, t3, this.options.preserveSymlinks, this.pluginDriver, this.resolveId, r2, s3, "boolean" == typeof i3 ? i3 : !t3, n2), t3, e4), n2), this.hasModuleSideEffects = s2.treeshake ? s2.treeshake.moduleSideEffects : () => true;
  }
  async addAdditionalModules(e3) {
    const t2 = this.extendLoadModulesPromise(Promise.all(e3.map((e4) => this.loadEntryModule(e4, false, void 0, null))));
    return await this.awaitLoadModulesPromise(), t2;
  }
  async addEntryModules(e3, t2) {
    const s2 = this.nextEntryModuleIndex;
    this.nextEntryModuleIndex += e3.length;
    const i2 = this.nextChunkNamePriority;
    this.nextChunkNamePriority += e3.length;
    const n2 = await this.extendLoadModulesPromise(Promise.all(e3.map(({ id: e4, importer: t3 }) => this.loadEntryModule(e4, true, t3, null))).then((n3) => {
      for (const [r2, o2] of n3.entries()) {
        o2.isUserDefinedEntryPoint = o2.isUserDefinedEntryPoint || t2, iu(o2, e3[r2], t2, i2 + r2);
        const n4 = this.indexedEntryModules.find((e4) => e4.module === o2);
        n4 ? n4.index = Math.min(n4.index, s2 + r2) : this.indexedEntryModules.push({ index: s2 + r2, module: o2 });
      }
      return this.indexedEntryModules.sort(({ index: e4 }, { index: t3 }) => e4 > t3 ? 1 : -1), n3;
    }));
    return await this.awaitLoadModulesPromise(), { entryModules: this.indexedEntryModules.map(({ module: e4 }) => e4), implicitEntryModules: [...this.implicitEntryModules], newEntryModules: n2 };
  }
  async emitChunk({ fileName: e3, id: t2, importer: s2, name: i2, implicitlyLoadedAfterOneOf: n2, preserveSignature: r2 }) {
    const o2 = { fileName: e3 || null, id: t2, importer: s2, name: i2 || null }, a2 = n2 ? await this.addEntryWithImplicitDependants(o2, n2) : (await this.addEntryModules([o2], false)).newEntryModules[0];
    return null != r2 && (a2.preserveSignature = r2), a2;
  }
  async preloadModule(e3) {
    return (await this.fetchModule(this.getResolvedIdWithDefaults(e3, fe), void 0, false, !e3.resolveDependencies || eu)).info;
  }
  addEntryWithImplicitDependants(e3, t2) {
    const s2 = this.nextChunkNamePriority++;
    return this.extendLoadModulesPromise(this.loadEntryModule(e3.id, false, e3.importer, null).then(async (i2) => {
      if (iu(i2, e3, false, s2), !i2.info.isEntry) {
        this.implicitEntryModules.add(i2);
        const s3 = await Promise.all(t2.map((t3) => this.loadEntryModule(t3, false, e3.importer, i2.id)));
        for (const e4 of s3)
          i2.implicitlyLoadedAfter.add(e4);
        for (const e4 of i2.implicitlyLoadedAfter)
          e4.implicitlyLoadedBefore.add(i2);
      }
      return i2;
    }));
  }
  async addModuleSource(e3, t2, s2) {
    let i2;
    try {
      i2 = await this.graph.fileOperationQueue.run(async () => await this.pluginDriver.hookFirst("load", [e3]) ?? await Lc(e3, "utf8"));
    } catch (s3) {
      let i3 = `Could not load ${e3}`;
      throw t2 && (i3 += ` (imported by ${T(t2)})`), i3 += `: ${s3.message}`, s3.message = i3, s3;
    }
    const n2 = "string" == typeof i2 ? { code: i2 } : null != i2 && "object" == typeof i2 && "string" == typeof i2.code ? i2 : Ye(function(e4) {
      return { code: "BAD_LOADER", message: `Error loading "${T(e4)}": plugin load hook should return a string, a { code, map } object, or nothing/null.` };
    }(e3)), r2 = this.graph.cachedModules.get(e3);
    if (!r2 || r2.customTransformCache || r2.originalCode !== n2.code || await this.pluginDriver.hookFirst("shouldTransformCachedModule", [{ ast: r2.ast, code: r2.code, id: r2.id, meta: r2.meta, moduleSideEffects: r2.moduleSideEffects, resolvedSources: r2.resolvedIds, syntheticNamedExports: r2.syntheticNamedExports }]))
      s2.updateOptions(n2), s2.setSource(await Jc(n2, s2, this.pluginDriver, this.options.onLog));
    else {
      if (r2.transformFiles)
        for (const e4 of r2.transformFiles)
          this.pluginDriver.emitFile(e4);
      s2.setSource(r2);
    }
  }
  async awaitLoadModulesPromise() {
    let e3;
    do {
      e3 = this.latestLoadModulesPromise, await e3;
    } while (e3 !== this.latestLoadModulesPromise);
  }
  extendLoadModulesPromise(e3) {
    return this.latestLoadModulesPromise = Promise.all([e3, this.latestLoadModulesPromise]), this.latestLoadModulesPromise.catch(() => {
    }), e3;
  }
  async fetchDynamicDependencies(e3, t2) {
    const s2 = await Promise.all(t2.map((t3) => t3.then(async ([t4, s3]) => null === s3 ? null : "string" == typeof s3 ? (t4.resolution = s3, null) : t4.resolution = await this.fetchResolvedDependency(T(s3.id), e3.id, s3))));
    for (const t3 of s2)
      t3 && (e3.dynamicDependencies.add(t3), t3.dynamicImporters.push(e3.id));
  }
  async fetchModule({ assertions: e3, id: t2, meta: s2, moduleSideEffects: i2, syntheticNamedExports: n2 }, r2, o2, a2) {
    const l2 = this.modulesById.get(t2);
    if (l2 instanceof Oo)
      return r2 && yo(e3, l2.info.assertions) && this.options.onLog(ve, Mt(l2.info.assertions, e3, t2, r2)), await this.handleExistingModule(l2, o2, a2), l2;
    const h2 = new Oo(this.graph, t2, this.options, o2, i2, n2, s2, e3);
    this.modulesById.set(t2, h2), this.graph.watchFiles[t2] = true;
    const c2 = this.addModuleSource(t2, r2, h2).then(() => [this.getResolveStaticDependencyPromises(h2), this.getResolveDynamicImportPromises(h2), u2]), u2 = ru(c2).then(() => this.pluginDriver.hookParallel("moduleParsed", [h2.info]));
    u2.catch(() => {
    }), this.moduleLoadPromises.set(h2, c2);
    const d2 = await c2;
    return a2 ? a2 === eu && await u2 : await this.fetchModuleDependencies(h2, ...d2), h2;
  }
  async fetchModuleDependencies(e3, t2, s2, i2) {
    this.modulesWithLoadedDependencies.has(e3) || (this.modulesWithLoadedDependencies.add(e3), await Promise.all([this.fetchStaticDependencies(e3, t2), this.fetchDynamicDependencies(e3, s2)]), e3.linkImports(), await i2);
  }
  fetchResolvedDependency(e3, t2, s2) {
    if (s2.external) {
      const { assertions: i2, external: n2, id: r2, moduleSideEffects: o2, meta: a2 } = s2;
      let l2 = this.modulesById.get(r2);
      if (l2) {
        if (!(l2 instanceof Qt))
          return Ye(function(e4, t3) {
            return { code: "INVALID_EXTERNAL_ID", message: `"${e4}" is imported as an external by "${T(t3)}", but is already an existing non-external module id.` };
          }(e3, t2));
        yo(l2.info.assertions, i2) && this.options.onLog(ve, Mt(l2.info.assertions, i2, e3, t2));
      } else
        l2 = new Qt(this.options, r2, o2, a2, "absolute" !== n2 && A(r2), i2), this.modulesById.set(r2, l2);
      return Promise.resolve(l2);
    }
    return this.fetchModule(s2, t2, false, false);
  }
  async fetchStaticDependencies(e3, t2) {
    for (const s2 of await Promise.all(t2.map((t3) => t3.then(([t4, s3]) => this.fetchResolvedDependency(t4, e3.id, s3)))))
      e3.dependencies.add(s2), s2.importers.push(e3.id);
    if (!this.options.treeshake || "no-treeshake" === e3.info.moduleSideEffects)
      for (const t3 of e3.dependencies)
        t3 instanceof Oo && (t3.importedFromNotTreeshaken = true);
  }
  getNormalizedResolvedIdWithoutDefaults(e3, t2, s2) {
    const { makeAbsoluteExternalsRelative: i2 } = this.options;
    if (e3) {
      if ("object" == typeof e3) {
        const n4 = e3.external || this.options.external(e3.id, t2, true);
        return { ...e3, external: n4 && ("relative" === n4 || !A(e3.id) || true === n4 && nu(e3.id, s2, i2) || "absolute") };
      }
      const n3 = this.options.external(e3, t2, true);
      return { external: n3 && (nu(e3, s2, i2) || "absolute"), id: n3 && i2 ? su(e3, t2) : e3 };
    }
    const n2 = i2 ? su(s2, t2) : s2;
    return false === e3 || this.options.external(n2, t2, true) ? { external: nu(n2, s2, i2) || "absolute", id: n2 } : null;
  }
  getResolveDynamicImportPromises(e3) {
    return e3.dynamicImports.map(async (t2) => {
      const s2 = await this.resolveDynamicImport(e3, "string" == typeof t2.argument ? t2.argument : t2.argument.esTreeNode, e3.id, function(e4) {
        var _a2, _b, _c2;
        const t3 = (_c2 = (_b = (_a2 = e4.arguments) == null ? void 0 : _a2[0]) == null ? void 0 : _b.properties.find((e5) => "assert" === go(e5))) == null ? void 0 : _c2.value;
        if (!t3)
          return fe;
        const s3 = t3.properties.map((e5) => {
          const t4 = go(e5);
          return "string" == typeof t4 && "string" == typeof e5.value.value ? [t4, e5.value.value] : null;
        }).filter((e5) => !!e5);
        return s3.length > 0 ? Object.fromEntries(s3) : fe;
      }(t2.node));
      return s2 && "object" == typeof s2 && (t2.id = s2.id), [t2, s2];
    });
  }
  getResolveStaticDependencyPromises(e3) {
    return Array.from(e3.sourcesWithAssertions, async ([t2, s2]) => [t2, e3.resolvedIds[t2] = e3.resolvedIds[t2] || this.handleInvalidResolvedId(await this.resolveId(t2, e3.id, fe, false, s2), t2, e3.id, s2)]);
  }
  getResolvedIdWithDefaults(e3, t2) {
    if (!e3)
      return null;
    const s2 = e3.external || false;
    return { assertions: e3.assertions || t2, external: s2, id: e3.id, meta: e3.meta || {}, moduleSideEffects: e3.moduleSideEffects ?? this.hasModuleSideEffects(e3.id, !!s2), resolvedBy: e3.resolvedBy ?? "rollup", syntheticNamedExports: e3.syntheticNamedExports ?? false };
  }
  async handleExistingModule(e3, t2, s2) {
    const i2 = this.moduleLoadPromises.get(e3);
    if (s2)
      return s2 === eu ? ru(i2) : i2;
    if (t2) {
      e3.info.isEntry = true, this.implicitEntryModules.delete(e3);
      for (const t3 of e3.implicitlyLoadedAfter)
        t3.implicitlyLoadedBefore.delete(e3);
      e3.implicitlyLoadedAfter.clear();
    }
    return this.fetchModuleDependencies(e3, ...await i2);
  }
  handleInvalidResolvedId(e3, t2, s2, i2) {
    return null === e3 ? k(t2) ? Ye(function(e4, t3) {
      return { code: $t, exporter: e4, id: t3, message: `Could not resolve "${e4}" from "${T(t3)}"` };
    }(t2, s2)) : (this.options.onLog(ve, function(e4, t3) {
      return { code: $t, exporter: e4, id: t3, message: `"${e4}" is imported by "${T(t3)}", but could not be resolved – treating it as an external dependency.`, url: Oe("troubleshooting/#warning-treating-module-as-external-dependency") };
    }(t2, s2)), { assertions: i2, external: true, id: t2, meta: {}, moduleSideEffects: this.hasModuleSideEffects(t2, true), resolvedBy: "rollup", syntheticNamedExports: false }) : (e3.external && e3.syntheticNamedExports && this.options.onLog(ve, function(e4, t3) {
      return { code: "EXTERNAL_SYNTHETIC_EXPORTS", exporter: e4, message: `External "${e4}" cannot have "syntheticNamedExports" enabled (imported by "${T(t3)}").` };
    }(t2, s2)), e3);
  }
  async loadEntryModule(e3, t2, s2, i2) {
    const n2 = await Mc(e3, s2, this.options.preserveSymlinks, this.pluginDriver, this.resolveId, null, fe, true, fe);
    return null == n2 ? Ye(null === i2 ? function(e4) {
      return { code: Ct, message: `Could not resolve entry module "${T(e4)}".` };
    }(e3) : function(e4, t3) {
      return { code: yt, message: `Module "${T(e4)}" that should be implicitly loaded before "${T(t3)}" could not be resolved.` };
    }(e3, i2)) : false === n2 || "object" == typeof n2 && n2.external ? Ye(null === i2 ? function(e4) {
      return { code: Ct, message: `Entry module "${T(e4)}" cannot be external.` };
    }(e3) : function(e4, t3) {
      return { code: yt, message: `Module "${T(e4)}" that should be implicitly loaded before "${T(t3)}" cannot be external.` };
    }(e3, i2)) : this.fetchModule(this.getResolvedIdWithDefaults("object" == typeof n2 ? n2 : { id: n2 }, fe), void 0, t2, false);
  }
  async resolveDynamicImport(e3, t2, s2, i2) {
    const n2 = await this.pluginDriver.hookFirst("resolveDynamicImport", [t2, s2, { assertions: i2 }]);
    if ("string" != typeof t2)
      return "string" == typeof n2 ? n2 : n2 ? this.getResolvedIdWithDefaults(n2, i2) : null;
    if (null == n2) {
      const n3 = e3.resolvedIds[t2];
      return n3 ? (yo(n3.assertions, i2) && this.options.onLog(ve, Mt(n3.assertions, i2, t2, s2)), n3) : e3.resolvedIds[t2] = this.handleInvalidResolvedId(await this.resolveId(t2, e3.id, fe, false, i2), t2, e3.id, i2);
    }
    return this.handleInvalidResolvedId(this.getResolvedIdWithDefaults(this.getNormalizedResolvedIdWithoutDefaults(n2, s2, t2), i2), t2, s2, i2);
  }
}
function su(e3, t2) {
  return k(e3) ? t2 ? N(t2, "..", e3) : N(e3) : e3;
}
function iu(e3, { fileName: t2, name: s2 }, i2, n2) {
  var _a2;
  if (null !== t2)
    e3.chunkFileNames.add(t2);
  else if (null !== s2) {
    let t3 = 0;
    for (; ((_a2 = e3.chunkNames[t3]) == null ? void 0 : _a2.priority) < n2; )
      t3++;
    e3.chunkNames.splice(t3, 0, { isUserDefined: i2, name: s2, priority: n2 });
  }
}
function nu(e3, t2, s2) {
  return true === s2 || "ifRelativeSource" === s2 && k(t2) || !A(e3);
}
async function ru(e3) {
  const [t2, s2] = await e3;
  return Promise.all([...t2, ...s2]);
}
class ou extends Li {
  constructor() {
    super(), this.parent = null, this.variables.set("undefined", new io());
  }
  findVariable(e3) {
    let t2 = this.variables.get(e3);
    return t2 || (t2 = new nn(e3), this.variables.set(e3, t2)), t2;
  }
}
function au(e3) {
  return Ll().update(e3).digest("hex");
}
function lu(e3, t2, s2, i2, n2) {
  const r2 = i2.sanitizeFileName(e3 || "asset");
  return ka(Aa("function" == typeof i2.assetFileNames ? i2.assetFileNames({ name: e3, source: t2, type: "asset" }) : i2.assetFileNames, "output.assetFileNames", { ext: () => C(r2).slice(1), extname: () => C(r2), hash: (e4) => s2.slice(0, Math.max(0, e4 || 8)), name: () => r2.slice(0, Math.max(0, r2.length - C(r2).length)) }), n2);
}
function hu(e3, { bundle: t2 }, s2) {
  t2[va].has(e3.toLowerCase()) ? s2(ve, function(e4) {
    return { code: ot, message: `The emitted file "${e4}" overwrites a previously emitted file of the same name.` };
  }(e3)) : t2[e3] = Sa;
}
const cu = /* @__PURE__ */ new Set(["chunk", "asset", "prebuilt-chunk"]);
function uu(e3, t2, s2) {
  if (!("string" == typeof e3 || e3 instanceof Uint8Array)) {
    const e4 = t2.fileName || t2.name || s2;
    return Ye(Kt(`Could not set source for ${"string" == typeof e4 ? `asset "${e4}"` : "unnamed asset"}, asset source needs to be a string, Uint8Array or Buffer.`));
  }
  return e3;
}
function du(e3, t2) {
  return "string" != typeof e3.fileName ? Ye((s2 = e3.name || t2, { code: et, message: `Plugin error - Unable to get file name for asset "${s2}". Ensure that the source is set and that generate is called first. If you reference assets via import.meta.ROLLUP_FILE_URL_<referenceId>, you need to either have set their source after "renderStart" or need to provide an explicit "fileName" when emitting them.` })) : e3.fileName;
  var s2;
}
function pu(e3, t2) {
  return e3.fileName ? e3.fileName : t2 ? t2.get(e3.module).getFileName() : Ye((s2 = e3.fileName || e3.name, { code: st, message: `Plugin error - Unable to get file name for emitted chunk "${s2}". You can only get file names once chunks have been generated after the "renderStart" hook.` }));
  var s2;
}
class fu {
  constructor(e3, t2, s2) {
    this.graph = e3, this.options = t2, this.facadeChunkByModule = null, this.nextIdBase = 1, this.output = null, this.outputFileEmitters = [], this.emitFile = (e4) => function(e5) {
      return Boolean(e5 && cu.has(e5.type));
    }(e4) ? "prebuilt-chunk" === e4.type ? this.emitPrebuiltChunk(e4) : function(e5) {
      const t3 = e5.fileName || e5.name;
      return !t3 || "string" == typeof t3 && !M(t3);
    }(e4) ? "chunk" === e4.type ? this.emitChunk(e4) : this.emitAsset(e4) : Ye(Kt(`The "fileName" or "name" properties of emitted chunks and assets must be strings that are neither absolute nor relative paths, received "${e4.fileName || e4.name}".`)) : Ye(Kt(`Emitted files must be of type "asset", "chunk" or "prebuilt-chunk", received "${e4 && e4.type}".`)), this.finaliseAssets = () => {
      for (const [e4, t3] of this.filesByReferenceId)
        if ("asset" === t3.type && "string" != typeof t3.fileName)
          return Ye({ code: "ASSET_SOURCE_MISSING", message: `Plugin error creating asset "${t3.name || e4}" - no asset source set.` });
    }, this.getFileName = (e4) => {
      const t3 = this.filesByReferenceId.get(e4);
      return t3 ? "chunk" === t3.type ? pu(t3, this.facadeChunkByModule) : "prebuilt-chunk" === t3.type ? t3.fileName : du(t3, e4) : Ye({ code: "FILE_NOT_FOUND", message: `Plugin error - Unable to get file name for unknown file "${e4}".` });
    }, this.setAssetSource = (e4, t3) => {
      const s3 = this.filesByReferenceId.get(e4);
      if (!s3)
        return Ye({ code: "ASSET_NOT_FOUND", message: `Plugin error - Unable to set the source for unknown asset "${e4}".` });
      if ("asset" !== s3.type)
        return Ye(Kt(`Asset sources can only be set for emitted assets but "${e4}" is an emitted chunk.`));
      if (void 0 !== s3.source)
        return Ye({ code: "ASSET_SOURCE_ALREADY_SET", message: `Unable to set the source for asset "${s3.name || e4}", source already set.` });
      const i2 = uu(t3, s3, e4);
      if (this.output)
        this.finalizeAdditionalAsset(s3, i2, this.output);
      else {
        s3.source = i2;
        for (const e5 of this.outputFileEmitters)
          e5.finalizeAdditionalAsset(s3, i2, e5.output);
      }
    }, this.setChunkInformation = (e4) => {
      this.facadeChunkByModule = e4;
    }, this.setOutputBundle = (e4, t3) => {
      const s3 = this.output = { bundle: e4, fileNamesBySource: /* @__PURE__ */ new Map(), outputOptions: t3 };
      for (const e5 of this.filesByReferenceId.values())
        e5.fileName && hu(e5.fileName, s3, this.options.onLog);
      const i2 = /* @__PURE__ */ new Map();
      for (const e5 of this.filesByReferenceId.values())
        if ("asset" === e5.type && void 0 !== e5.source)
          if (e5.fileName)
            this.finalizeAdditionalAsset(e5, e5.source, s3);
          else {
            F(i2, au(e5.source), () => []).push(e5);
          }
        else
          "prebuilt-chunk" === e5.type && (this.output.bundle[e5.fileName] = this.createPrebuiltChunk(e5));
      for (const [e5, t4] of i2)
        this.finalizeAssetsWithSameSource(t4, e5, s3);
    }, this.filesByReferenceId = s2 ? new Map(s2.filesByReferenceId) : /* @__PURE__ */ new Map(), s2 == null ? void 0 : s2.addOutputFileEmitter(this);
  }
  addOutputFileEmitter(e3) {
    this.outputFileEmitters.push(e3);
  }
  assignReferenceId(e3, t2) {
    let s2 = t2;
    do {
      s2 = Ll().update(s2).digest("hex").slice(0, 8);
    } while (this.filesByReferenceId.has(s2) || this.outputFileEmitters.some(({ filesByReferenceId: e4 }) => e4.has(s2)));
    e3.referenceId = s2, this.filesByReferenceId.set(s2, e3);
    for (const { filesByReferenceId: t3 } of this.outputFileEmitters)
      t3.set(s2, e3);
    return s2;
  }
  createPrebuiltChunk(e3) {
    return { code: e3.code, dynamicImports: [], exports: e3.exports || [], facadeModuleId: null, fileName: e3.fileName, implicitlyLoadedBefore: [], importedBindings: {}, imports: [], isDynamicEntry: false, isEntry: false, isImplicitEntry: false, map: e3.map || null, moduleIds: [], modules: {}, name: e3.fileName, referencedFiles: [], type: "chunk" };
  }
  emitAsset(e3) {
    const t2 = void 0 === e3.source ? void 0 : uu(e3.source, e3, null), s2 = { fileName: e3.fileName, name: e3.name, needsCodeReference: !!e3.needsCodeReference, referenceId: "", source: t2, type: "asset" }, i2 = this.assignReferenceId(s2, e3.fileName || e3.name || String(this.nextIdBase++));
    if (this.output)
      this.emitAssetWithReferenceId(s2, this.output);
    else
      for (const e4 of this.outputFileEmitters)
        e4.emitAssetWithReferenceId(s2, e4.output);
    return i2;
  }
  emitAssetWithReferenceId(e3, t2) {
    const { fileName: s2, source: i2 } = e3;
    s2 && hu(s2, t2, this.options.onLog), void 0 !== i2 && this.finalizeAdditionalAsset(e3, i2, t2);
  }
  emitChunk(e3) {
    if (this.graph.phase > fo.LOAD_AND_PARSE)
      return Ye({ code: pt, message: "Cannot emit chunks after module loading has finished." });
    if ("string" != typeof e3.id)
      return Ye(Kt(`Emitted chunks need to have a valid string id, received "${e3.id}"`));
    const t2 = { fileName: e3.fileName, module: null, name: e3.name || e3.id, referenceId: "", type: "chunk" };
    return this.graph.moduleLoader.emitChunk(e3).then((e4) => t2.module = e4).catch(() => {
    }), this.assignReferenceId(t2, e3.id);
  }
  emitPrebuiltChunk(e3) {
    if ("string" != typeof e3.code)
      return Ye(Kt(`Emitted prebuilt chunks need to have a valid string code, received "${e3.code}".`));
    if ("string" != typeof e3.fileName || M(e3.fileName))
      return Ye(Kt(`The "fileName" property of emitted prebuilt chunks must be strings that are neither absolute nor relative paths, received "${e3.fileName}".`));
    const t2 = { code: e3.code, exports: e3.exports, fileName: e3.fileName, map: e3.map, referenceId: "", type: "prebuilt-chunk" }, s2 = this.assignReferenceId(t2, t2.fileName);
    return this.output && (this.output.bundle[t2.fileName] = this.createPrebuiltChunk(t2)), s2;
  }
  finalizeAdditionalAsset(e3, t2, { bundle: s2, fileNamesBySource: i2, outputOptions: n2 }) {
    let { fileName: r2, needsCodeReference: o2, referenceId: a2 } = e3;
    if (!r2) {
      const o3 = au(t2);
      r2 = i2.get(o3), r2 || (r2 = lu(e3.name, t2, o3, n2, s2), i2.set(o3, r2));
    }
    const l2 = { ...e3, fileName: r2, source: t2 };
    this.filesByReferenceId.set(a2, l2);
    const h2 = s2[r2];
    "asset" === (h2 == null ? void 0 : h2.type) ? h2.needsCodeReference && (h2.needsCodeReference = o2) : s2[r2] = { fileName: r2, name: e3.name, needsCodeReference: o2, source: t2, type: "asset" };
  }
  finalizeAssetsWithSameSource(e3, t2, { bundle: s2, fileNamesBySource: i2, outputOptions: n2 }) {
    let r2, o2 = "", a2 = true;
    for (const i3 of e3) {
      a2 && (a2 = i3.needsCodeReference);
      const e4 = lu(i3.name, i3.source, t2, n2, s2);
      (!o2 || e4.length < o2.length || e4.length === o2.length && e4 < o2) && (o2 = e4, r2 = i3);
    }
    i2.set(t2, o2);
    for (const t3 of e3) {
      const e4 = { ...t3, fileName: o2 };
      this.filesByReferenceId.set(t3.referenceId, e4);
    }
    s2[o2] = { fileName: o2, name: r2.name, needsCodeReference: a2, source: r2.source, type: "asset" };
  }
}
function mu(e3, t2, s2, i2, n2) {
  return ke[e3] < ke[n2] ? Fi : (n3, r2) => {
    null != r2 && s2(ve, { code: ct, message: `Plugin "${i2}" tried to add a file position to a log or warning. This is only supported in the "transform" hook at the moment and will be ignored.` }), (n3 = Wc(n3)).code && !n3.pluginCode && (n3.pluginCode = n3.code), n3.code = t2, n3.plugin = i2, s2(e3, n3);
  };
}
function gu(t2, s2, i2, n2, r2, o2) {
  const { logLevel: a2, onLog: l2 } = n2;
  let h2, c2 = true;
  if ("string" != typeof t2.cacheKey && (t2.name.startsWith(Vc) || t2.name.startsWith(Bc) || o2.has(t2.name) ? c2 = false : o2.add(t2.name)), s2)
    if (c2) {
      const e3 = t2.cacheKey || t2.name;
      d2 = s2[e3] || (s2[e3] = /* @__PURE__ */ Object.create(null)), h2 = { delete: (e4) => delete d2[e4], get(e4) {
        const t3 = d2[e4];
        if (t3)
          return t3[0] = 0, t3[1];
      }, has(e4) {
        const t3 = d2[e4];
        return !!t3 && (t3[0] = 0, true);
      }, set(e4, t3) {
        d2[e4] = [0, t3];
      } };
    } else
      u2 = t2.name, h2 = { delete: () => Fc(u2), get: () => Fc(u2), has: () => Fc(u2), set: () => Fc(u2) };
  else
    h2 = zc;
  var u2, d2;
  return { addWatchFile(e3) {
    if (i2.phase >= fo.GENERATE)
      return this.error({ code: pt, message: 'Cannot call "addWatchFile" after the build has finished.' });
    i2.watchFiles[e3] = true;
  }, cache: h2, debug: mu(Ae, "PLUGIN_LOG", l2, t2.name, a2), emitFile: r2.emitFile.bind(r2), error: (e3) => Ye(Gt(Wc(e3), t2.name)), getFileName: r2.getFileName, getModuleIds: () => i2.modulesById.keys(), getModuleInfo: i2.getModuleInfo, getWatchFiles: () => Object.keys(i2.watchFiles), info: mu(Se, "PLUGIN_LOG", l2, t2.name, a2), load: (e3) => i2.moduleLoader.preloadModule(e3), meta: { rollupVersion: e, watchMode: i2.watchMode }, get moduleIds() {
    const e3 = i2.modulesById.keys();
    return function* () {
      Yt(`Accessing "this.moduleIds" on the plugin context by plugin ${t2.name} is deprecated. The "this.getModuleIds" plugin context function should be used instead.`, "plugin-development/#this-getmoduleids", true, n2, t2.name), yield* e3;
    }();
  }, parse: i2.contextParse.bind(i2), resolve: (e3, s3, { assertions: n3, custom: r3, isEntry: o3, skipSelf: a3 } = pe) => i2.moduleLoader.resolveId(e3, s3, r3, o3, n3 || fe, a3 ? [{ importer: s3, plugin: t2, source: e3 }] : null), setAssetSource: r2.setAssetSource, warn: mu(ve, "PLUGIN_WARNING", l2, t2.name, a2) };
}
const yu = Object.keys({ buildEnd: 1, buildStart: 1, closeBundle: 1, closeWatcher: 1, load: 1, moduleParsed: 1, onLog: 1, options: 1, resolveDynamicImport: 1, resolveId: 1, shouldTransformCachedModule: 1, transform: 1, watchChange: 1 });
class xu {
  constructor(e3, t2, s2, i2, n2) {
    this.graph = e3, this.options = t2, this.pluginCache = i2, this.sortedPlugins = /* @__PURE__ */ new Map(), this.unfulfilledActions = /* @__PURE__ */ new Set(), this.fileEmitter = new fu(e3, t2, n2 && n2.fileEmitter), this.emitFile = this.fileEmitter.emitFile.bind(this.fileEmitter), this.getFileName = this.fileEmitter.getFileName.bind(this.fileEmitter), this.finaliseAssets = this.fileEmitter.finaliseAssets.bind(this.fileEmitter), this.setChunkInformation = this.fileEmitter.setChunkInformation.bind(this.fileEmitter), this.setOutputBundle = this.fileEmitter.setOutputBundle.bind(this.fileEmitter), this.plugins = [...n2 ? n2.plugins : [], ...s2];
    const r2 = /* @__PURE__ */ new Set();
    if (this.pluginContexts = new Map(this.plugins.map((s3) => [s3, gu(s3, i2, e3, t2, this.fileEmitter, r2)])), n2)
      for (const e4 of s2)
        for (const s3 of yu)
          s3 in e4 && t2.onLog(ve, (o2 = e4.name, { code: "INPUT_HOOK_IN_OUTPUT_PLUGIN", message: `The "${s3}" hook used by the output plugin ${o2} is a build time hook and will not be run for that plugin. Either this plugin cannot be used as an output plugin, or it should have an option to configure it as an output plugin.` }));
    var o2;
  }
  createOutputPluginDriver(e3) {
    return new xu(this.graph, this.options, e3, this.pluginCache, this);
  }
  getUnfulfilledHookActions() {
    return this.unfulfilledActions;
  }
  hookFirst(e3, t2, s2, i2) {
    return this.hookFirstAndGetPlugin(e3, t2, s2, i2).then((e4) => e4 && e4[0]);
  }
  async hookFirstAndGetPlugin(e3, t2, s2, i2) {
    for (const n2 of this.getSortedPlugins(e3)) {
      if (i2 == null ? void 0 : i2.has(n2))
        continue;
      const r2 = await this.runHook(e3, t2, n2, s2);
      if (null != r2)
        return [r2, n2];
    }
    return null;
  }
  hookFirstSync(e3, t2, s2) {
    for (const i2 of this.getSortedPlugins(e3)) {
      const n2 = this.runHookSync(e3, t2, i2, s2);
      if (null != n2)
        return n2;
    }
    return null;
  }
  async hookParallel(e3, t2, s2) {
    const i2 = [];
    for (const n2 of this.getSortedPlugins(e3))
      n2[e3].sequential ? (await Promise.all(i2), i2.length = 0, await this.runHook(e3, t2, n2, s2)) : i2.push(this.runHook(e3, t2, n2, s2));
    await Promise.all(i2);
  }
  hookReduceArg0(e3, [t2, ...s2], i2, n2) {
    let r2 = Promise.resolve(t2);
    for (const t3 of this.getSortedPlugins(e3))
      r2 = r2.then((r3) => this.runHook(e3, [r3, ...s2], t3, n2).then((e4) => i2.call(this.pluginContexts.get(t3), r3, e4, t3)));
    return r2;
  }
  hookReduceArg0Sync(e3, [t2, ...s2], i2, n2) {
    for (const r2 of this.getSortedPlugins(e3)) {
      const o2 = [t2, ...s2], a2 = this.runHookSync(e3, o2, r2, n2);
      t2 = i2.call(this.pluginContexts.get(r2), t2, a2, r2);
    }
    return t2;
  }
  async hookReduceValue(e3, t2, s2, i2) {
    const n2 = [], r2 = [];
    for (const t3 of this.getSortedPlugins(e3, vu))
      t3[e3].sequential ? (n2.push(...await Promise.all(r2)), r2.length = 0, n2.push(await this.runHook(e3, s2, t3))) : r2.push(this.runHook(e3, s2, t3));
    return n2.push(...await Promise.all(r2)), n2.reduce(i2, await t2);
  }
  hookReduceValueSync(e3, t2, s2, i2, n2) {
    let r2 = t2;
    for (const t3 of this.getSortedPlugins(e3)) {
      const o2 = this.runHookSync(e3, s2, t3, n2);
      r2 = i2.call(this.pluginContexts.get(t3), r2, o2, t3);
    }
    return r2;
  }
  hookSeq(e3, t2, s2) {
    let i2 = Promise.resolve();
    for (const n2 of this.getSortedPlugins(e3))
      i2 = i2.then(() => this.runHook(e3, t2, n2, s2));
    return i2.then(Su);
  }
  getSortedPlugins(e3, t2) {
    return F(this.sortedPlugins, e3, () => Eu(e3, this.plugins, t2));
  }
  runHook(e3, t2, s2, i2) {
    const n2 = s2[e3], r2 = "object" == typeof n2 ? n2.handler : n2;
    let o2 = this.pluginContexts.get(s2);
    i2 && (o2 = i2(o2, s2));
    let a2 = null;
    return Promise.resolve().then(() => {
      if ("function" != typeof r2)
        return r2;
      const i3 = r2.apply(o2, t2);
      return (i3 == null ? void 0 : i3.then) ? (a2 = [s2.name, e3, t2], this.unfulfilledActions.add(a2), Promise.resolve(i3).then((e4) => (this.unfulfilledActions.delete(a2), e4))) : i3;
    }).catch((t3) => (null !== a2 && this.unfulfilledActions.delete(a2), Ye(Gt(t3, s2.name, { hook: e3 }))));
  }
  runHookSync(e3, t2, s2, i2) {
    const n2 = s2[e3], r2 = "object" == typeof n2 ? n2.handler : n2;
    let o2 = this.pluginContexts.get(s2);
    i2 && (o2 = i2(o2, s2));
    try {
      return r2.apply(o2, t2);
    } catch (t3) {
      return Ye(Gt(t3, s2.name, { hook: e3 }));
    }
  }
}
function Eu(e3, t2, s2 = bu) {
  const i2 = [], n2 = [], r2 = [];
  for (const o2 of t2) {
    const t3 = o2[e3];
    if (t3) {
      if ("object" == typeof t3) {
        if (s2(t3.handler, e3, o2), "pre" === t3.order) {
          i2.push(o2);
          continue;
        }
        if ("post" === t3.order) {
          r2.push(o2);
          continue;
        }
      } else
        s2(t3, e3, o2);
      n2.push(o2);
    }
  }
  return [...i2, ...n2, ...r2];
}
function bu(e3, t2, s2) {
  "function" != typeof e3 && Ye(function(e4, t3) {
    return { code: dt, hook: e4, message: `Error running plugin hook "${e4}" for plugin "${t3}", expected a function hook or an object with a "handler" function.`, plugin: t3 };
  }(t2, s2.name));
}
function vu(e3, t2, s2) {
  if ("string" != typeof e3 && "function" != typeof e3)
    return Ye(function(e4, t3) {
      return { code: dt, hook: e4, message: `Error running plugin hook "${e4}" for plugin "${t3}", expected a string, a function hook or an object with a "handler" string or function.`, plugin: t3 };
    }(t2, s2.name));
}
function Su() {
}
class Au {
  constructor(e3) {
    this.maxParallel = e3, this.queue = [], this.workerCount = 0;
  }
  run(e3) {
    return new Promise((t2, s2) => {
      this.queue.push({ reject: s2, resolve: t2, task: e3 }), this.work();
    });
  }
  async work() {
    if (this.workerCount >= this.maxParallel)
      return;
    let e3;
    for (this.workerCount++; e3 = this.queue.shift(); ) {
      const { reject: t2, resolve: s2, task: i2 } = e3;
      try {
        s2(await i2());
      } catch (e4) {
        t2(e4);
      }
    }
    this.workerCount--;
  }
}
class ku {
  constructor(e3, t2) {
    var _a2, _b;
    if (this.options = e3, this.astLru = function(e4) {
      var t3, s2, i2, n2 = e4 || 1;
      function r2(e5, r3) {
        ++t3 > n2 && (i2 = s2, o2(1), ++t3), s2[e5] = r3;
      }
      function o2(e5) {
        t3 = 0, s2 = /* @__PURE__ */ Object.create(null), e5 || (i2 = /* @__PURE__ */ Object.create(null));
      }
      return o2(), { clear: o2, has: function(e5) {
        return void 0 !== s2[e5] || void 0 !== i2[e5];
      }, get: function(e5) {
        var t4 = s2[e5];
        return void 0 !== t4 ? t4 : void 0 !== (t4 = i2[e5]) ? (r2(e5, t4), t4) : void 0;
      }, set: function(e5, t4) {
        void 0 !== s2[e5] ? s2[e5] = t4 : r2(e5, t4);
      } };
    }(5), this.cachedModules = /* @__PURE__ */ new Map(), this.deoptimizationTracker = new J(), this.entryModules = [], this.modulesById = /* @__PURE__ */ new Map(), this.needsTreeshakingPass = false, this.phase = fo.LOAD_AND_PARSE, this.scope = new ou(), this.watchFiles = /* @__PURE__ */ Object.create(null), this.watchMode = false, this.externalModules = [], this.implicitEntryModules = [], this.modules = [], this.getModuleInfo = (e4) => {
      const t3 = this.modulesById.get(e4);
      return t3 ? t3.info : null;
    }, false !== e3.cache) {
      if ((_a2 = e3.cache) == null ? void 0 : _a2.modules)
        for (const t3 of e3.cache.modules)
          this.cachedModules.set(t3.id, t3);
      this.pluginCache = ((_b = e3.cache) == null ? void 0 : _b.plugins) || /* @__PURE__ */ Object.create(null);
      for (const e4 in this.pluginCache) {
        const t3 = this.pluginCache[e4];
        for (const e5 of Object.values(t3))
          e5[0]++;
      }
    }
    if (t2) {
      this.watchMode = true;
      const e4 = (...e5) => this.pluginDriver.hookParallel("watchChange", e5), s2 = () => this.pluginDriver.hookParallel("closeWatcher", []);
      t2.onCurrentRun("change", e4), t2.onCurrentRun("close", s2);
    }
    this.pluginDriver = new xu(this, e3, e3.plugins, this.pluginCache), this.acornParser = $h.extend(...e3.acornInjectPlugins), this.moduleLoader = new tu(this, this.modulesById, this.options, this.pluginDriver), this.fileOperationQueue = new Au(e3.maxParallelFileOps), this.pureFunctions = (({ treeshake: e4 }) => {
      const t3 = /* @__PURE__ */ Object.create(null);
      for (const s2 of e4 ? e4.manualPureFunctions : []) {
        let e5 = t3;
        for (const t4 of s2.split("."))
          e5 = e5[t4] || (e5[t4] = /* @__PURE__ */ Object.create(null));
        e5[zi] = true;
      }
      return t3;
    })(e3);
  }
  async build() {
    Io("generate module graph", 2), await this.generateModuleGraph(), wo("generate module graph", 2), Io("sort and bind modules", 2), this.phase = fo.ANALYSE, this.sortModules(), wo("sort and bind modules", 2), Io("mark included statements", 2), this.includeStatements(), wo("mark included statements", 2), this.phase = fo.GENERATE;
  }
  contextParse(e3, t2 = {}) {
    const s2 = t2.onComment, i2 = [];
    t2.onComment = s2 && "function" == typeof s2 ? (e4, n3, r2, o2, ...a2) => (i2.push({ end: o2, start: r2, type: e4 ? "Block" : "Line", value: n3 }), s2.call(t2, e4, n3, r2, o2, ...a2)) : i2;
    const n2 = this.acornParser.parse(e3, { ...this.options.acorn, ...t2 });
    return "object" == typeof s2 && s2.push(...i2), t2.onComment = s2, function(e4, t3, s3) {
      const i3 = [], n3 = [];
      for (const t4 of e4) {
        for (const [e5, s4] of Ks)
          s4.test(t4.value) && i3.push({ ...t4, annotationType: e5 });
        zs.test(t4.value) && n3.push(t4);
      }
      for (const e5 of n3)
        Ys(t3, e5, false);
      Us(t3, { annotationIndex: 0, annotations: i3, code: s3 });
    }(i2, n2, e3), n2;
  }
  getCache() {
    for (const e3 in this.pluginCache) {
      const t2 = this.pluginCache[e3];
      let s2 = true;
      for (const [e4, i2] of Object.entries(t2))
        i2[0] >= this.options.experimentalCacheExpiry ? delete t2[e4] : s2 = false;
      s2 && delete this.pluginCache[e3];
    }
    return { modules: this.modules.map((e3) => e3.toJSON()), plugins: this.pluginCache };
  }
  async generateModuleGraph() {
    var e3;
    if ({ entryModules: this.entryModules, implicitEntryModules: this.implicitEntryModules } = await this.moduleLoader.addEntryModules((e3 = this.options.input, Array.isArray(e3) ? e3.map((e4) => ({ fileName: null, id: e4, implicitlyLoadedAfter: [], importer: void 0, name: null })) : Object.entries(e3).map(([e4, t2]) => ({ fileName: null, id: t2, implicitlyLoadedAfter: [], importer: void 0, name: e4 }))), true), 0 === this.entryModules.length)
      throw new Error("You must supply options.input to rollup");
    for (const e4 of this.modulesById.values())
      e4 instanceof Oo ? this.modules.push(e4) : this.externalModules.push(e4);
  }
  includeStatements() {
    const e3 = [...this.entryModules, ...this.implicitEntryModules];
    for (const t2 of e3)
      $o(t2);
    if (this.options.treeshake) {
      let t2 = 1;
      do {
        Io(`treeshaking pass ${t2}`, 3), this.needsTreeshakingPass = false;
        for (const e4 of this.modules)
          e4.isExecuted && ("no-treeshake" === e4.info.moduleSideEffects ? e4.includeAllInBundle() : e4.include());
        if (1 === t2)
          for (const t3 of e3)
            false !== t3.preserveSignature && (t3.includeAllExports(false), this.needsTreeshakingPass = true);
        wo("treeshaking pass " + t2++, 3);
      } while (this.needsTreeshakingPass);
    } else
      for (const e4 of this.modules)
        e4.includeAllInBundle();
    for (const e4 of this.externalModules)
      e4.warnUnusedImports();
    for (const e4 of this.implicitEntryModules)
      for (const t2 of e4.implicitlyLoadedAfter)
        t2.info.isEntry || t2.isIncluded() || Ye(jt(t2));
  }
  sortModules() {
    const { orderedModules: e3, cyclePaths: t2 } = function(e4) {
      let t3 = 0;
      const s2 = [], i2 = /* @__PURE__ */ new Set(), n2 = /* @__PURE__ */ new Set(), r2 = /* @__PURE__ */ new Map(), o2 = [], a2 = (e5) => {
        if (e5 instanceof Oo) {
          for (const t4 of e5.dependencies)
            r2.has(t4) ? i2.has(t4) || s2.push(Ga(t4, e5, r2)) : (r2.set(t4, e5), a2(t4));
          for (const t4 of e5.implicitlyLoadedBefore)
            n2.add(t4);
          for (const { resolution: t4 } of e5.dynamicImports)
            t4 instanceof Oo && n2.add(t4);
          o2.push(e5);
        }
        e5.execIndex = t3++, i2.add(e5);
      };
      for (const t4 of e4)
        r2.has(t4) || (r2.set(t4, null), a2(t4));
      for (const e5 of n2)
        r2.has(e5) || (r2.set(e5, null), a2(e5));
      return { cyclePaths: s2, orderedModules: o2 };
    }(this.entryModules);
    for (const e4 of t2)
      this.options.onLog(ve, Dt(e4));
    this.modules = e3;
    for (const e4 of this.modules)
      e4.bindReferences();
    this.warnForMissingExports();
  }
  warnForMissingExports() {
    for (const e3 of this.modules)
      for (const t2 of e3.importDescriptions.values())
        "*" === t2.name || t2.module.getVariableForExportName(t2.name)[0] || e3.log(ve, Ft(t2.name, e3.id, t2.module.id), t2.start);
  }
}
function Iu(e3, t2) {
  return t2();
}
function wu(t2, s2, i2, n2) {
  t2 = Eu("onLog", t2);
  const r2 = ke[n2], o2 = (n3, a2, l2 = ge) => {
    if (!(ke[n3] < r2)) {
      for (const s3 of t2) {
        if (l2.has(s3))
          continue;
        const { onLog: t3 } = s3, h2 = (e3) => ke[e3] < r2 ? Fi : (t4) => o2(e3, Wc(t4), new Set(l2).add(s3));
        if (false === ("handler" in t3 ? t3.handler : t3).call({ debug: h2(Ae), error: (e3) => Ye(Wc(e3)), info: h2(Se), meta: { rollupVersion: e, watchMode: i2 }, warn: h2(ve) }, n3, a2))
          return;
      }
      s2(n3, a2);
    }
  };
  return o2;
}
const Pu = "{".charCodeAt(0), Cu = " ".charCodeAt(0), $u = "assert";
function Nu(e3) {
  const t2 = e3.acorn || Rc, { tokTypes: s2, TokenType: i2 } = t2;
  return class extends e3 {
    constructor(...e4) {
      super(...e4), this.assertToken = new i2($u);
    }
    _codeAt(e4) {
      return this.input.charCodeAt(e4);
    }
    _eat(e4) {
      this.type !== e4 && this.unexpected(), this.next();
    }
    readToken(e4) {
      let t3 = 0;
      for (; t3 < 6; t3++)
        if (this._codeAt(this.pos + t3) !== $u.charCodeAt(t3))
          return super.readToken(e4);
      for (; this._codeAt(this.pos + t3) !== Pu; t3++)
        if (this._codeAt(this.pos + t3) !== Cu)
          return super.readToken(e4);
      return "{" === this.type.label ? super.readToken(e4) : (this.pos += 6, this.finishToken(this.assertToken));
    }
    parseDynamicImport(e4) {
      if (this.next(), e4.source = this.parseMaybeAssign(), this.eat(s2.comma)) {
        const t3 = this.parseObj(false);
        e4.arguments = [t3];
      }
      return this._eat(s2.parenR), this.finishNode(e4, "ImportExpression");
    }
    parseExport(e4, t3) {
      if (this.next(), this.eat(s2.star)) {
        if (this.options.ecmaVersion >= 11 && (this.eatContextual("as") ? (e4.exported = this.parseIdent(true), this.checkExport(t3, e4.exported.name, this.lastTokStart)) : e4.exported = null), this.expectContextual("from"), this.type !== s2.string && this.unexpected(), e4.source = this.parseExprAtom(), this.type === this.assertToken || this.type === s2._with) {
          this.next();
          const t4 = this.parseImportAssertions();
          t4 && (e4.assertions = t4);
        }
        return this.semicolon(), this.finishNode(e4, "ExportAllDeclaration");
      }
      if (this.eat(s2._default)) {
        var i3;
        if (this.checkExport(t3, "default", this.lastTokStart), this.type === s2._function || (i3 = this.isAsyncFunction())) {
          var n2 = this.startNode();
          this.next(), i3 && this.next(), e4.declaration = this.parseFunction(n2, 5, false, i3);
        } else if (this.type === s2._class) {
          var r2 = this.startNode();
          e4.declaration = this.parseClass(r2, "nullableID");
        } else
          e4.declaration = this.parseMaybeAssign(), this.semicolon();
        return this.finishNode(e4, "ExportDefaultDeclaration");
      }
      if (this.shouldParseExportStatement())
        e4.declaration = this.parseStatement(null), "VariableDeclaration" === e4.declaration.type ? this.checkVariableExport(t3, e4.declaration.declarations) : this.checkExport(t3, e4.declaration.id.name, e4.declaration.id.start), e4.specifiers = [], e4.source = null;
      else {
        if (e4.declaration = null, e4.specifiers = this.parseExportSpecifiers(t3), this.eatContextual("from")) {
          if (this.type !== s2.string && this.unexpected(), e4.source = this.parseExprAtom(), this.type === this.assertToken || this.type === s2._with) {
            this.next();
            const t4 = this.parseImportAssertions();
            t4 && (e4.assertions = t4);
          }
        } else {
          for (var o2 = 0, a2 = e4.specifiers; o2 < a2.length; o2 += 1) {
            var l2 = a2[o2];
            this.checkUnreserved(l2.local), this.checkLocalExport(l2.local);
          }
          e4.source = null;
        }
        this.semicolon();
      }
      return this.finishNode(e4, "ExportNamedDeclaration");
    }
    parseImport(e4) {
      if (this.next(), this.type === s2.string ? (e4.specifiers = [], e4.source = this.parseExprAtom()) : (e4.specifiers = this.parseImportSpecifiers(), this.expectContextual("from"), e4.source = this.type === s2.string ? this.parseExprAtom() : this.unexpected()), this.type === this.assertToken || this.type == s2._with) {
        this.next();
        const t3 = this.parseImportAssertions();
        t3 && (e4.assertions = t3);
      }
      return this.semicolon(), this.finishNode(e4, "ImportDeclaration");
    }
    parseImportAssertions() {
      this._eat(s2.braceL);
      const e4 = this.parseAssertEntries();
      return this._eat(s2.braceR), e4;
    }
    parseAssertEntries() {
      const e4 = [], t3 = /* @__PURE__ */ new Set();
      do {
        if (this.type === s2.braceR)
          break;
        const i3 = this.startNode();
        let n2;
        n2 = this.type === s2.string ? this.parseLiteral(this.value) : this.parseIdent(true), this.next(), i3.key = n2, t3.has(i3.key.name) && this.raise(this.pos, "Duplicated key in assertions"), t3.add(i3.key.name), this.type !== s2.string && this.raise(this.pos, "Only string is supported as an assertion value"), i3.value = this.parseLiteral(this.value), e4.push(this.finishNode(i3, "ImportAttribute"));
      } while (this.eat(s2.comma));
      return e4;
    }
  };
}
function _u(e3) {
  return Array.isArray(e3) ? e3.filter(Boolean) : e3 ? [e3] : [];
}
const Ru = (e3) => ({ ecmaVersion: "latest", sourceType: "module", ...e3.acorn }), Ou = (e3) => [Nu, ..._u(e3.acornInjectPlugins)], Du = (e3) => {
  var _a2;
  return true === e3.cache ? void 0 : ((_a2 = e3.cache) == null ? void 0 : _a2.cache) || e3.cache;
}, Lu = (e3) => {
  if (true === e3)
    return () => true;
  if ("function" == typeof e3)
    return (t2, ...s2) => !t2.startsWith("\0") && e3(t2, ...s2) || false;
  if (e3) {
    const t2 = /* @__PURE__ */ new Set(), s2 = [];
    for (const i2 of _u(e3))
      i2 instanceof RegExp ? s2.push(i2) : t2.add(i2);
    return (e4, ...i2) => t2.has(e4) || s2.some((t3) => t3.test(e4));
  }
  return () => false;
}, Tu = (e3, t2, s2) => {
  const i2 = e3.inlineDynamicImports;
  return i2 && Xt('The "inlineDynamicImports" option is deprecated. Use the "output.inlineDynamicImports" option instead.', Ue, true, t2, s2), i2;
}, Mu = (e3) => {
  const t2 = e3.input;
  return null == t2 ? [] : "string" == typeof t2 ? [t2] : t2;
}, Vu = (e3, t2, s2) => {
  const i2 = e3.manualChunks;
  return i2 && Xt('The "manualChunks" option is deprecated. Use the "output.manualChunks" option instead.', We, true, t2, s2), i2;
}, Bu = (e3, t2, s2) => {
  const i2 = e3.maxParallelFileReads;
  "number" == typeof i2 && Xt('The "maxParallelFileReads" option is deprecated. Use the "maxParallelFileOps" option instead.', "configuration-options/#maxparallelfileops", true, t2, s2);
  const n2 = e3.maxParallelFileOps ?? i2;
  return "number" == typeof n2 ? n2 <= 0 ? 1 / 0 : n2 : 20;
}, zu = (e3, t2) => {
  const s2 = e3.moduleContext;
  if ("function" == typeof s2)
    return (e4) => s2(e4) ?? t2;
  if (s2) {
    const e4 = /* @__PURE__ */ Object.create(null);
    for (const [t3, i2] of Object.entries(s2))
      e4[N(t3)] = i2;
    return (s3) => e4[s3] ?? t2;
  }
  return () => t2;
}, Fu = (e3, t2, s2) => {
  const i2 = e3.preserveModules;
  return i2 && Xt('The "preserveModules" option is deprecated. Use the "output.preserveModules" option instead.', "configuration-options/#output-preservemodules", true, t2, s2), i2;
}, ju = (e3) => {
  if (false === e3.treeshake)
    return false;
  const t2 = Qc(e3.treeshake, Yc, "treeshake", "configuration-options/#treeshake", "false, true, ");
  return { annotations: false !== t2.annotations, correctVarValueBeforeDeclaration: true === t2.correctVarValueBeforeDeclaration, manualPureFunctions: t2.manualPureFunctions ?? me, moduleSideEffects: Uu(t2.moduleSideEffects), propertyReadSideEffects: "always" === t2.propertyReadSideEffects ? "always" : false !== t2.propertyReadSideEffects, tryCatchDeoptimization: false !== t2.tryCatchDeoptimization, unknownGlobalSideEffects: false !== t2.unknownGlobalSideEffects };
}, Uu = (e3) => {
  if ("boolean" == typeof e3)
    return () => e3;
  if ("no-external" === e3)
    return (e4, t2) => !t2;
  if ("function" == typeof e3)
    return (t2, s2) => !!t2.startsWith("\0") || false !== e3(t2, s2);
  if (Array.isArray(e3)) {
    const t2 = new Set(e3);
    return (e4) => t2.has(e4);
  }
  return e3 && Ye(zt("treeshake.moduleSideEffects", "configuration-options/#treeshake-modulesideeffects", 'please use one of false, "no-external", a function or an array')), () => true;
}, Gu = /[\u0000-\u001F"#$&*+,:;<=>?[\]^`{|}\u007F]/g, Wu = /^[a-z]:/i;
function qu(e3) {
  const t2 = Wu.exec(e3), s2 = t2 ? t2[0] : "";
  return s2 + e3.slice(s2.length).replace(Gu, "_");
}
const Hu = (e3, t2, s2) => {
  const { file: i2 } = e3;
  if ("string" == typeof i2) {
    if (t2)
      return Ye(zt("output.file", Me, 'you must set "output.dir" instead of "output.file" when using the "output.preserveModules" option'));
    if (!Array.isArray(s2.input))
      return Ye(zt("output.file", Me, 'you must set "output.dir" instead of "output.file" when providing named inputs'));
  }
  return i2;
}, Ku = (e3) => {
  const t2 = e3.format;
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
      return Ye(zt("output.format", ze, 'Valid values are "amd", "cjs", "system", "es", "iife" or "umd"', t2));
  }
}, Yu = (e3, t2) => {
  const s2 = (e3.inlineDynamicImports ?? t2.inlineDynamicImports) || false, { input: i2 } = t2;
  return s2 && (Array.isArray(i2) ? i2 : Object.keys(i2)).length > 1 ? Ye(zt("output.inlineDynamicImports", Ue, 'multiple inputs are not supported when "output.inlineDynamicImports" is true')) : s2;
}, Xu = (e3, t2, s2) => {
  const i2 = (e3.preserveModules ?? s2.preserveModules) || false;
  if (i2) {
    if (t2)
      return Ye(zt("output.inlineDynamicImports", Ue, 'this option is not supported for "output.preserveModules"'));
    if (false === s2.preserveEntrySignatures)
      return Ye(zt("preserveEntrySignatures", "configuration-options/#preserveentrysignatures", 'setting this option to false is not supported for "output.preserveModules"'));
  }
  return i2;
}, Qu = (e3, t2) => {
  const s2 = e3.preferConst;
  return null != s2 && Yt('The "output.preferConst" option is deprecated. Use the "output.generatedCode.constBindings" option instead.', "configuration-options/#output-generatedcode-constbindings", true, t2), !!s2;
}, Zu = (e3) => {
  const { preserveModulesRoot: t2 } = e3;
  if (null != t2)
    return N(t2);
}, Ju = (e3) => {
  const t2 = { autoId: false, basePath: "", define: "define", forceJsExtensionForImports: false, ...e3.amd };
  return (t2.autoId || t2.basePath) && t2.id ? Ye(zt("output.amd.id", Te, 'this option cannot be used together with "output.amd.autoId"/"output.amd.basePath"')) : t2.basePath && !t2.autoId ? Ye(zt("output.amd.basePath", "configuration-options/#output-amd-basepath", 'this option only works with "output.amd.autoId"')) : t2.autoId ? { autoId: true, basePath: t2.basePath, define: t2.define, forceJsExtensionForImports: t2.forceJsExtensionForImports } : { autoId: false, define: t2.define, forceJsExtensionForImports: t2.forceJsExtensionForImports, id: t2.id };
}, ed = (e3, t2) => {
  const s2 = e3[t2];
  return "function" == typeof s2 ? s2 : () => s2 || "";
}, td = (e3, t2) => {
  const { dir: s2 } = e3;
  return "string" == typeof s2 && "string" == typeof t2 ? Ye(zt("output.dir", Me, 'you must set either "output.file" for a single-file build or "output.dir" when generating multiple chunks')) : s2;
}, sd = (e3, t2, s2) => {
  const i2 = e3.dynamicImportFunction;
  return i2 && (Yt('The "output.dynamicImportFunction" option is deprecated. Use the "renderDynamicImport" plugin hook instead.', "plugin-development/#renderdynamicimport", true, t2), "es" !== s2 && t2.onLog(ve, zt("output.dynamicImportFunction", "configuration-options/#output-dynamicimportfunction", 'this option is ignored for formats other than "es"'))), i2;
}, id = (e3, t2) => {
  const s2 = e3.entryFileNames;
  return null == s2 && t2.add("entryFileNames"), s2 ?? "[name].js";
};
function nd(e3, t2) {
  const s2 = e3.experimentalDeepDynamicChunkOptimization;
  return null != s2 && Yt('The "output.experimentalDeepDynamicChunkOptimization" option is deprecated as Rollup always runs the full chunking algorithm now. The option should be removed.', Fe, true, t2), s2 || false;
}
function rd(e3, t2) {
  const s2 = e3.exports;
  if (null == s2)
    t2.add("exports");
  else if (!["default", "named", "none", "auto"].includes(s2))
    return Ye({ code: ht, message: `"output.exports" must be "default", "named", "none", "auto", or left unspecified (defaults to "auto"), received "${s2}".`, url: Oe(Ve) });
  return s2 || "auto";
}
const od = (e3, t2) => {
  const s2 = Qc(e3.generatedCode, Xc, "output.generatedCode", "configuration-options/#output-generatedcode", "");
  return { arrowFunctions: true === s2.arrowFunctions, constBindings: true === s2.constBindings || t2, objectShorthand: true === s2.objectShorthand, reservedNamesAsProps: false !== s2.reservedNamesAsProps, symbols: true === s2.symbols };
}, ad = (e3, t2) => {
  if (t2)
    return "";
  const s2 = e3.indent;
  return false === s2 ? "" : s2 ?? true;
}, ld = /* @__PURE__ */ new Set(["compat", "auto", "esModule", "default", "defaultOnly"]), hd = (e3) => {
  const t2 = e3.interop;
  if ("function" == typeof t2) {
    const e4 = /* @__PURE__ */ Object.create(null);
    let s2 = null;
    return (i2) => null === i2 ? s2 || cd(s2 = t2(i2)) : i2 in e4 ? e4[i2] : cd(e4[i2] = t2(i2));
  }
  return void 0 === t2 ? () => "default" : () => cd(t2);
}, cd = (e3) => ld.has(e3) ? e3 : Ye(zt("output.interop", Ge, `use one of ${Array.from(ld, (e4) => JSON.stringify(e4)).join(", ")}`, e3)), ud = (e3, t2, s2, i2) => {
  const n2 = e3.manualChunks || i2.manualChunks;
  if (n2) {
    if (t2)
      return Ye(zt("output.manualChunks", We, 'this option is not supported for "output.inlineDynamicImports"'));
    if (s2)
      return Ye(zt("output.manualChunks", We, 'this option is not supported for "output.preserveModules"'));
  }
  return n2 || {};
}, dd = (e3, t2, s2) => e3.minifyInternalExports ?? (s2 || "es" === t2 || "system" === t2), pd = (e3, t2, s2) => {
  const i2 = e3.namespaceToStringTag;
  return null != i2 ? (Yt('The "output.namespaceToStringTag" option is deprecated. Use the "output.generatedCode.symbols" option instead.', "configuration-options/#output-generatedcode-symbols", true, s2), i2) : t2.symbols || false;
}, fd = (e3) => {
  const { sourcemapBaseUrl: t2 } = e3;
  if (t2)
    return function(e4) {
      try {
        new URL(e4);
      } catch {
        return false;
      }
      return true;
    }(t2) ? (s2 = t2).endsWith("/") ? s2 : s2 + "/" : Ye(zt("output.sourcemapBaseUrl", "configuration-options/#output-sourcemapbaseurl", `must be a valid URL, received ${JSON.stringify(t2)}`));
  var s2;
};
function md(t2) {
  return async function(t3, s2) {
    const { options: i2, unsetOptions: n2 } = await async function(t4, s3) {
      if (!t4)
        throw new Error("You must supply an options object to rollup");
      const i3 = await async function(t5, s4) {
        const i4 = Eu("options", await Zc(t5.plugins)), n4 = t5.logLevel || Se, r4 = wu(i4, jc(t5, n4), s4, n4);
        for (const o3 of i4) {
          const { name: i5, options: a3 } = o3, l2 = "handler" in a3 ? a3.handler : a3, h2 = await l2.call({ debug: mu(Ae, "PLUGIN_LOG", r4, i5, n4), error: (e3) => Ye(Gt(Wc(e3), i5, { hook: "onLog" })), info: mu(Se, "PLUGIN_LOG", r4, i5, n4), meta: { rollupVersion: e, watchMode: s4 }, warn: mu(ve, "PLUGIN_WARNING", r4, i5, n4) }, t5);
          h2 && (t5 = h2);
        }
        return t5;
      }(t4, s3), { options: n3, unsetOptions: r3 } = await async function(e3, t5) {
        const s4 = /* @__PURE__ */ new Set(), i4 = e3.context ?? "undefined", n4 = await Zc(e3.plugins), r4 = e3.logLevel || Se, o3 = wu(n4, jc(e3, r4), t5, r4), a3 = e3.strictDeprecations || false, l2 = Bu(e3, o3, a3), h2 = { acorn: Ru(e3), acornInjectPlugins: Ou(e3), cache: Du(e3), context: i4, experimentalCacheExpiry: e3.experimentalCacheExpiry ?? 10, experimentalLogSideEffects: e3.experimentalLogSideEffects || false, external: Lu(e3.external), inlineDynamicImports: Tu(e3, o3, a3), input: Mu(e3), logLevel: r4, makeAbsoluteExternalsRelative: e3.makeAbsoluteExternalsRelative ?? "ifRelativeSource", manualChunks: Vu(e3, o3, a3), maxParallelFileOps: l2, maxParallelFileReads: l2, moduleContext: zu(e3, i4), onLog: o3, onwarn: (e4) => o3(ve, e4), perf: e3.perf || false, plugins: n4, preserveEntrySignatures: e3.preserveEntrySignatures ?? "exports-only", preserveModules: Fu(e3, o3, a3), preserveSymlinks: e3.preserveSymlinks || false, shimMissingExports: e3.shimMissingExports || false, strictDeprecations: a3, treeshake: ju(e3) };
        return Kc(e3, [...Object.keys(h2), "watch"], "input options", o3, /^(output)$/), { options: h2, unsetOptions: s4 };
      }(i3, s3);
      return gd(n3.plugins, Vc), { options: n3, unsetOptions: r3 };
    }(t3, null !== s2);
    !function(e3) {
      e3.perf ? (bo = /* @__PURE__ */ new Map(), Io = So, wo = Ao, e3.plugins = e3.plugins.map(Co)) : (Io = Fi, wo = Fi);
    }(i2);
    const r2 = new ku(i2, s2), o2 = false !== t3.cache;
    t3.cache && (i2.cache = void 0, t3.cache = void 0);
    Io("BUILD", 1), await Iu(r2.pluginDriver, async () => {
      try {
        Io("initialize", 2), await r2.pluginDriver.hookParallel("buildStart", [i2]), wo("initialize", 2), await r2.build();
      } catch (e3) {
        const t4 = Object.keys(r2.watchFiles);
        throw t4.length > 0 && (e3.watchFiles = t4), await r2.pluginDriver.hookParallel("buildEnd", [e3]), await r2.pluginDriver.hookParallel("closeBundle", []), e3;
      }
      await r2.pluginDriver.hookParallel("buildEnd", []);
    }), wo("BUILD", 1);
    const a2 = { cache: o2 ? r2.getCache() : void 0, async close() {
      a2.closed || (a2.closed = true, await r2.pluginDriver.hookParallel("closeBundle", []));
    }, closed: false, generate: async (e3) => a2.closed ? Ye(_t()) : yd(false, i2, n2, e3, r2), watchFiles: Object.keys(r2.watchFiles), write: async (e3) => a2.closed ? Ye(_t()) : yd(true, i2, n2, e3, r2) };
    i2.perf && (a2.getTimings = ko);
    return a2;
  }(t2, null);
}
function gd(e3, t2) {
  for (const [s2, i2] of e3.entries())
    i2.name || (i2.name = `${t2}${s2 + 1}`);
}
async function yd(e3, t2, s2, i2, n2) {
  const { options: r2, outputPluginDriver: o2, unsetOptions: a2 } = await async function(e4, t3, s3, i3) {
    if (!e4)
      throw new Error("You must supply an options object");
    const n3 = await Zc(e4.plugins);
    gd(n3, Bc);
    const r3 = t3.createOutputPluginDriver(n3);
    return { ...await xd(s3, i3, e4, r3), outputPluginDriver: r3 };
  }(i2, n2.pluginDriver, t2, s2);
  return Iu(0, async () => {
    const s3 = new zl(r2, a2, t2, o2, n2), i3 = await s3.generate(e3);
    if (e3) {
      if (Io("WRITE", 1), !r2.dir && !r2.file)
        return Ye({ code: bt, message: 'You must specify "output.file" or "output.dir" for the build.', url: Oe(Me) });
      await Promise.all(Object.values(i3).map((e4) => n2.fileOperationQueue.run(() => async function(e5, t3) {
        const s4 = N(t3.dir || P(t3.file), e5.fileName);
        return await Dc(P(s4), { recursive: true }), Tc(s4, "asset" === e5.type ? e5.source : e5.code);
      }(e4, r2)))), await o2.hookParallel("writeBundle", [r2, i3]), wo("WRITE", 1);
    }
    return l2 = i3, { output: Object.values(l2).filter((e4) => Object.keys(e4).length > 0).sort((e4, t3) => bd(e4) - bd(t3)) };
    var l2;
  });
}
function xd(e3, t2, s2, i2) {
  return async function(e4, t3, s3) {
    const i3 = new Set(s3), n2 = e4.compact || false, r2 = Ku(e4), o2 = Yu(e4, t3), a2 = Xu(e4, o2, t3), l2 = Hu(e4, a2, t3), h2 = Qu(e4, t3), c2 = od(e4, h2), u2 = { amd: Ju(e4), assetFileNames: e4.assetFileNames ?? "assets/[name]-[hash][extname]", banner: ed(e4, "banner"), chunkFileNames: e4.chunkFileNames ?? "[name]-[hash].js", compact: n2, dir: td(e4, l2), dynamicImportFunction: sd(e4, t3, r2), dynamicImportInCjs: e4.dynamicImportInCjs ?? true, entryFileNames: id(e4, i3), esModule: e4.esModule ?? "if-default-prop", experimentalDeepDynamicChunkOptimization: nd(e4, t3), experimentalMinChunkSize: e4.experimentalMinChunkSize ?? 1, exports: rd(e4, i3), extend: e4.extend || false, externalImportAssertions: e4.externalImportAssertions ?? true, externalLiveBindings: e4.externalLiveBindings ?? true, file: l2, footer: ed(e4, "footer"), format: r2, freeze: e4.freeze ?? true, generatedCode: c2, globals: e4.globals || {}, hoistTransitiveImports: e4.hoistTransitiveImports ?? true, indent: ad(e4, n2), inlineDynamicImports: o2, interop: hd(e4), intro: ed(e4, "intro"), manualChunks: ud(e4, o2, a2, t3), minifyInternalExports: dd(e4, r2, n2), name: e4.name, namespaceToStringTag: pd(e4, c2, t3), noConflict: e4.noConflict || false, outro: ed(e4, "outro"), paths: e4.paths || {}, plugins: await Zc(e4.plugins), preferConst: h2, preserveModules: a2, preserveModulesRoot: Zu(e4), sanitizeFileName: "function" == typeof e4.sanitizeFileName ? e4.sanitizeFileName : false === e4.sanitizeFileName ? (e5) => e5 : qu, sourcemap: e4.sourcemap || false, sourcemapBaseUrl: fd(e4), sourcemapExcludeSources: e4.sourcemapExcludeSources || false, sourcemapFile: e4.sourcemapFile, sourcemapIgnoreList: "function" == typeof e4.sourcemapIgnoreList ? e4.sourcemapIgnoreList : false === e4.sourcemapIgnoreList ? () => false : (e5) => e5.includes("node_modules"), sourcemapPathTransform: e4.sourcemapPathTransform, strict: e4.strict ?? true, systemNullSetters: e4.systemNullSetters ?? true, validate: e4.validate || false };
    return Kc(e4, Object.keys(u2), "output options", t3.onLog), { options: u2, unsetOptions: i3 };
  }(i2.hookReduceArg0Sync("outputOptions", [s2], (e4, t3) => t3 || e4, (e4) => {
    const t3 = () => e4.error({ code: tt, message: 'Cannot emit files or set asset sources in the "outputOptions" hook, use the "renderStart" hook instead.' });
    return { ...e4, emitFile: t3, setAssetSource: t3 };
  }), e3, t2);
}
var Ed;
function bd(e3) {
  return "asset" === e3.type ? Ed.ASSET : e3.isEntry ? Ed.ENTRY_CHUNK : Ed.SECONDARY_CHUNK;
}
!function(e3) {
  e3[e3.ENTRY_CHUNK = 0] = "ENTRY_CHUNK", e3[e3.SECONDARY_CHUNK = 1] = "SECONDARY_CHUNK", e3[e3.ASSET = 2] = "ASSET";
}(Ed || (Ed = {}));
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
          if (!importee.endsWith(".js")) {
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
  const bundle = await md(inputOptions);
  const generated = await bundle.generate(outputOptions);
  let { code } = generated.output[0];
  code = code.replace(/\'\.\/@nop\/utils\.js\'/g, "'@nop/utils'");
  return code;
}
export {
  rollupTransform
};
