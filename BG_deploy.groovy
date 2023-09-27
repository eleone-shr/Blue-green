def req = ["env":"",
                   "changeRequest":""]
def env = '$(env)'
def buildID = '$(buildID)'
def Recipients = "examplename1@exampleemail.com, examplename2@exampleemail.com"

def Yamldata = [
    'apiVersion': 'v1',
    'environment': env,
    'version': 1.0,
    'appFamily': 'iep-test',
    'appName': 'bg-sample',
    'buildID': buildID,
    'kind': 'deployment',
    'manifest': 'lines',
    'deployment': [ 
        'target': 'EKS',
        'style': 'non-intrusive',
        'window': 'asap'
    ],
    'email' : 'emails'
]

node {
    stage('Write Yaml') {
        writeYaml file: 'manifest.yaml', data: Yamldata, overwrite: true
        sh 'cat manifest.yaml'
        archiveArtifacts artifacts: "manifest.yaml", fingerprint: true
    }

    stage('Env Select') {
        envTestList = ["DSIT","SITE","PTE","PROD","BLUE-GREEN","END"]
		
		req = ""
		
        req = input message: "Select an environment to deploy Artifact?",
            id: 'envResponse',
                parameters: [string(description: 'Change Request number from KISAM', name: 'changeRequest'),
                        choice(name: 'env',
                        choices: envTestList)]
        println(req)

        if (req.env == 'BLUE-GREEN') {
            echo 'Blue-green deployment selected'
            iepBlueGreen(req)
        }
    }
}

def iepBlueGreen(req) {
    stage('Blue-Green Deploy') {

        copyArtifacts(projectName: env.JOB_NAME, selector: specific(env.BUILD_NUMBER), filter: 'manifest.yaml')

        def file = readYaml file: "manifest.yaml"

        response = input message: "Fill out the following for a blue-green manifest.",
                        id: 'bgResponse',
                        parameters: [string(description: 'Current Version in BG', name: 'greenVersion'),
                                    string(description: 'Updating Version', name: 'blueVersion', defaultValue: "1.0"),
                                    string(description: 'Current Build ID in BG', name: 'greenBuildID'),
                                    string(description: 'Updating Build ID', name: 'blueBuildID', defaultValue: "${BUILD_ID}"),
                                    string(description: 'Blue Weight', name: 'blueWeight'),
                                    string(description: 'Green Weight', name: 'greenWeight'),
                                    choice(name: 'environment',
                                    choices: envTestList)]
        println(response)

       

        file['environment'] = response["environment"].toLowerCase()
        file['version'] = response["blueVersion"] + "," + response["greenVersion"]
        file['buildID'] = response["greenBuildID"] + "," + response["blueBuildID"]
        file['kind'] = "promotion"

        file["deployment"]["type"] = "BG"
        file["changeRequest"] = req["changeRequest"]

        try {
            def blueWeight = response["blueWeight"].toInteger()
            def greenWeight = response["greenWeight"].toInteger() 
            if (blueWeight + greenWeight == 100) {
                file['deployment']["blue"] = ["weight": blueWeight ]
                file['deployment']["green"] = ["weight": greenWeight ]
            } else {
                throw new Exception("Weights do not add up to 100")
            }
        } catch (Exception exc) {
            echo "Blue green weight not inputted correctly!"
            throw exc
        }


        // sh """
        //         if [ -f manifest.yaml ] ; then
        //                 rm -f manifest.yaml
        //         fi
        // """

        writeYaml file: 'manifest.yaml', data: file, overwrite:true


        sh 'cat manifest.yaml'
    }
}
                                  
