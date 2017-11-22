goog.module('missing.extend');

//!! Using class syntax requires default externs but the test runs faster
//!! without them. Just use @constructor instead.

/**
 * @constructor
 * @extends {direct.ref.A}
 */
function B() {

}

/**
 * @constructor
 * @extends {direct.ref.ATemplated<string, number>}
 */
  function BTemplated() {

}

const C = goog.require('missing.base');
/**
 * @constructor
 * @extends {C}
 */
function D() {

}

/**
 * @param {C!} c
 */
function E(c) {

}

/** @type {C} */
const F = null;

exports.B = B;
exports.BTemplated = BTemplated;
exports.D = D;
exports.E = E;
exports.F = F;
