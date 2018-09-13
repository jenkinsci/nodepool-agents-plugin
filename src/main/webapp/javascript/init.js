/*
 * Initialize the data table.  Specifies a few options:
 * - sets the table length (number of rows) options to custom values
 * - sets a default order
 * - Save the state of a table (its paging position, ordering state etc) so that is can be restored when the user
 *   reloads a page, or comes back to the page after visiting a sub-page.
 *
 * More fun options here: https://datatables.net/examples/basic_init/
 */
$(document).ready(function () {
    $('#nodepool-table').DataTable({
        "lengthMenu": [[25, 50, 75, -1], [25, 50, 75, "All"]],
        "order": [[0, "asc"]],
        stateSave: true,
        // Need a custom renderer for NodePool Cluster column
        "columnDefs": [{
            "targets": 4,
            "render": function (data, type, row, meta) {
                return data.replace(new RegExp(',', 'g'), ' ');
            }
        }],
        // oSearch defines the global filtering state at initialisation time
        // < 1.10 syntax => "oSearch": {"sSearch": "nodepool"}
        "search": {"search": "nodepool"}
    });
});
