/*
 * Waltz - Enterprise Architecture
 * Copyright (C) 2016, 2017, 2018, 2019 Waltz open source project
 * See README.md for more information
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import _ from "lodash";
import {nest} from "d3-collection";


export const formats = {
    daysAndMinutes: "ddd Do MMM YYYY - HH:mm:ss",
    daysOnly: "ddd Do MMM YYYY",
    parse: "YYYY-MM-DDThh:mm:ss.SSS",
    parseDateOnly: "YYYY-MM-DD"
};


export const entityLifecycleStatuses = {
    ACTIVE: "ACTIVE",
    PENDING: "PENDING",
    REMOVED: "REMOVED"
};


export function notEmpty(xs) {
    return ! _.isEmpty(xs);
}


export function isEmpty(xs) {
    return _.isEmpty(xs);
}


export function ifPresent(obj, fn, dflt) {
    return obj
        ? fn(obj)
        : dflt;
}


export function randomPick(xs) {
    if (!xs) throw new Error("Cannot pick from a null set of options");

    const choiceCount = xs.length - 1;
    const idx = Math.round(Math.random() * choiceCount);
    return xs[idx];
}


/**
 * Attempts to return the result of the given function.
 * If the function throws an exception the default value
 * will be returned
 *
 * @param fn
 * @param dflt  value to return if fn throws an exception
 * @returns {*}
 */
export function perhaps(fn, dflt) {
    try {
        return fn();
    } catch (e) {
        return dflt;
    }
}


/**
 *
 * @param items - items to be searched
 * @param searchStr - query string to search for
 * @param searchFields - fields in the items to consider when searching, may be a function
 * @returns {Array}
 */
export function termSearch(items = [],
                           searchStr = "",
                           searchFields = []) {
    if (_.isEmpty(searchStr)) {
        return items;
    }

    const terms = searchStr.toLowerCase().split(/\W/);

    const getSearchFieldsForItem = item => _.isEmpty(searchFields)
        ? _.chain(item)
            .keys()
            .reject(field => field.startsWith("$") || _.isFunction(_.get(item, field)))
            .value()
        : searchFields;

    return _.filter(items, item => {
        const targetStr = _
            .chain(getSearchFieldsForItem(item))
            .map(field => _.isFunction(field)
                ? field(item)
                : _.get(item, [ field ], ""))
            .map(v => v.toLowerCase())
            .join(" ")
            .value()
            .toLowerCase();

        return _.every(terms, term => targetStr.includes(term));
    });
}


/**
 * the d3 nest function aggregates using the property name 'values', this
 * function creates a copy of the data with the name 'count'.
 *
 * @param data
 * @returns {Array|*}
 */
function toCountData(data = []) {
    return _.map(
        data,
        d => ({
            key: d.key,
            count: d.value
        }));
}


export function toKeyCounts(items = [], keyFn = x => x) {
    if (! items) return [];
    return toCountData(nest()
        .key(keyFn)
        .rollup(d => d.length)
        .entries(items));
}


export function resetData(vm, initData = {}) {
    return Object.assign(vm, _.cloneDeep(initData));
}


/**
 * Deep copies `initData` into `vm`
 * @param vm
 * @param initData
 * @returns {*} - `vm` enriched with `initData`
 */
export function initialiseData(vm, initData) {
    return _.defaultsDeep(vm, _.cloneDeep(initData));
}



/**
 * Invokes a function and also passes in any provided arguments in order
 * e.g. invokeFunction(onClick, arg1, arg2)
 * @param fn
 * @returns {*}
 */
export function invokeFunction(fn) {
    if (_.isFunction(fn)) {
        const parameters = _.slice(arguments, 1);
        return fn(...parameters);
    }
    console.log("invokeFunction - attempted to invoke empty function: ", fn)
    return null;
}

