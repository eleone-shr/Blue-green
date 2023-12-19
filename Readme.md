**ECAD CICD Pipeline Enhancements**

**Major Changes:**
1. **7/26/22**
   - Added the “ENVIRONMENT_TEST_LIST” value to the Jenkins parameter file
   - Consolidated the amount of input responses for approved submitters
2. **8/25/22**
   - Logic incorporated to request and allow for deployments to the PSU and PROD environments
   - pipelineChanges.log is now included as an archived artifact to review change to the CICD_IEP_Template.
   - Changed input request timeout from 3 days to 30 days

**Minor Changes:**
1. **7/26/22**
   - Added timestamp echo to console to view deployment time/date upon sending artifacts to the IEP
   - Added the console log and changeset.zip into the main zip package for audit/traceability purposes
   - Added a getAllChanges.groovy function to create changeset.html
   - Added ‘PTE’ as a drop-down option as a default value
