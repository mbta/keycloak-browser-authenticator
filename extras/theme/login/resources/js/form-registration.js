/* Registration Form Script */

(function() {

	"use strict";

	var webForm = {

		initialized: false,

		initialize: function() {
			if (this.initialized) return;
			this.initialized = true;

			this.events();
		},

		events: function() {
			$("#token").val('this must be empty').delay(2000).queue(function() {
				$("#token").val((new Date).getFullYear());
	        }); 
		}
	};

	webForm.initialize();

})();
