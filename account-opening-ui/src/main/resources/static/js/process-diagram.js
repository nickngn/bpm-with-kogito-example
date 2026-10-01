/*
 * Renders the Account Opening BPMN with bpmn-js and paints live process state on
 * top of it: completed nodes, running nodes, and who each running user task is
 * waiting on. State comes from /processes/{id}/diagram-state (see
 * ProcessDiagramController), which proxies the Kogito Data Index.
 */
(function () {
    'use strict';

    var BPMN_URL = '/bpmn/ntb.bpmn2';
    var REFRESH_MS = 5000;

    var container = document.getElementById('bpmn-canvas');
    if (!container || typeof BpmnJS === 'undefined') {
        return;
    }

    var processInstanceId = container.dataset.processInstanceId;
    if (!processInstanceId) {
        return;
    }

    var viewer = new BpmnJS({ container: container });
    var canvas = viewer.get('canvas');
    var overlays = viewer.get('overlays');
    var elementRegistry = viewer.get('elementRegistry');

    // elementId -> taskId, for click-through to the task form.
    var taskByElement = {};
    var markedElements = [];

    viewer.get('eventBus').on('element.click', function (event) {
        var taskId = taskByElement[event.element.id];
        if (taskId) {
            window.location.href = '/tasks/' + encodeURIComponent(taskId);
        }
    });

    function mark(elementId, cssClass) {
        if (!elementRegistry.get(elementId)) {
            return false;
        }
        canvas.addMarker(elementId, cssClass);
        markedElements.push([elementId, cssClass]);
        return true;
    }

    function clearMarkers() {
        markedElements.forEach(function (entry) {
            canvas.removeMarker(entry[0], entry[1]);
        });
        markedElements = [];
        overlays.clear();
        taskByElement = {};
    }

    function badgeHtml(node) {
        var who;
        if (node.owner) {
            who = '<b>' + escapeHtml(node.owner) + '</b>';
        } else if (node.groups && node.groups.length) {
            who = 'unclaimed &middot; ' + escapeHtml(node.groups.join(', '));
        } else {
            who = 'unassigned';
        }
        var status = node.status ? escapeHtml(node.status) + ' &middot; ' : '';
        return '<div class="bpmn-badge' + (node.mine ? ' is-mine' : '') + '">'
            + status + who + '</div>';
    }

    function escapeHtml(value) {
        return String(value).replace(/[&<>"']/g, function (c) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
        });
    }

    function paint(state) {
        clearMarkers();

        (state.completed || []).forEach(function (elementId) {
            mark(elementId, 'node-done');
        });

        (state.active || []).forEach(function (node) {
            if (!mark(node.elementId, node.mine ? 'node-mine' : 'node-active')) {
                return;
            }
            if (node.taskId) {
                taskByElement[node.elementId] = node.taskId;
                mark(node.elementId, 'node-clickable');
            }
            if (node.type === 'HumanTaskNode') {
                overlays.add(node.elementId, 'owner', {
                    position: { bottom: -6, left: 0 },
                    html: badgeHtml(node)
                });
            }
        });
    }

    function fetchState() {
        return fetch('/processes/' + encodeURIComponent(processInstanceId) + '/diagram-state',
            { headers: { 'Accept': 'application/json' } })
            .then(function (response) {
                if (!response.ok) {
                    throw new Error('diagram-state ' + response.status);
                }
                return response.json();
            });
    }

    fetch(BPMN_URL)
        .then(function (response) { return response.text(); })
        .then(function (xml) { return viewer.importXML(xml); })
        .then(function (result) {
            // The .bpmn2 carries drools: attributes bpmn-moddle does not know.
            // Those come back as warnings and are safe to ignore.
            if (result && result.warnings && result.warnings.length) {
                console.debug('bpmn-js import warnings:', result.warnings);
            }
            canvas.zoom('fit-viewport');
            return fetchState().then(paint);
        })
        .catch(function (error) {
            console.error('Could not render process diagram', error);
            container.innerHTML = '<p style="padding:1rem;color:#b00;">'
                + 'Could not load the process diagram.</p>';
        });

    setInterval(function () {
        fetchState().then(paint).catch(function (error) {
            console.debug('diagram refresh failed', error);
        });
    }, REFRESH_MS);
}());
